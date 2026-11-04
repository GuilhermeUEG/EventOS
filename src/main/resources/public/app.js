'use strict';

/* EventOS — site público.
   Cliente da API REST compartilhada com o desktop: nenhuma regra de negócio é decidida aqui.
   Ocultar botões por perfil é apenas conveniência; o servidor valida toda operação protegida. */

// ---------------------------------------------------------------- estado
const GUEST = { id: null, name: 'Visitante', email: '', role: 'GUEST' };
const ROLE_LABELS = { ADMIN: 'Administrador', ORGANIZER: 'Organizador', PARTICIPANT: 'Participante', GUEST: 'Visitante' };
const ATTENDANCE_LABELS = { PRESENT: 'Presente', PARTIAL: 'Parcial', ABSENT: 'Ausente' };
const TABS = ['home', 'prog', 'agenda', 'cert', 'admin', 'users'];
const QR_IMAGE_PATTERN = /^data:image\/png;base64,[A-Za-z0-9+/=]+$/;

let authToken = null;
let activeUser = GUEST;
let currentEventId = null;
let eventData = null;
let editingEventId = null;
let adminDatesEventId = null;
let activities = [];
let programActivities = [];
let userRegs = [];
let eligibility = null;
let demoAccounts = [];
let lastProjectorToken = null;
let qrTimer = null;
let qrExpiresAt = 0;
let programSeq = 0;
let searchTimer = null;
let builderCounter = 0;
let focusBeforeModal = null;
let currentFeedbackSurvey = null;
let currentTab = 'prog';

// ---------------------------------------------------------------- utilidades
function $(id) { return document.getElementById(id); }

function escapeHtml(value) {
    return String(value === null || value === undefined ? '' : value)
        .replaceAll('&', '&amp;').replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;').replaceAll('"', '&quot;')
        .replaceAll("'", '&#039;');
}

function toId(value) {
    const n = Number(value);
    return Number.isFinite(n) ? Math.trunc(n) : 0;
}

function initials(name) {
    return String(name || '?').split(' ').filter(Boolean).map(n => n[0]).slice(0, 2).join('').toUpperCase() || '?';
}

function isLoggedIn() { return Boolean(authToken && activeUser && activeUser.id); }
function isStaff() { return activeUser.role === 'ORGANIZER' || activeUser.role === 'ADMIN'; }
function isAdmin() { return activeUser.role === 'ADMIN'; }
/** RN-17: só o administrador ou o organizador responsável altera o evento (o servidor confere de novo). */
function canManageEvent() {
    return isLoggedIn() && isStaff() && Boolean(eventData) && (isAdmin() || eventData.organizerId === activeUser.id);
}

function parseDate(d) {
    if (!d) return null;
    if (Array.isArray(d)) return new Date(d[0], d[1] - 1, d[2], d[3] || 0, d[4] || 0);
    return new Date(d);
}

function validDate(d) { return d instanceof Date && !isNaN(d.getTime()); }

function formatTime(d) {
    const dt = parseDate(d);
    return validDate(dt) ? dt.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' }) : '--:--';
}

function formatDate(d) {
    const dt = parseDate(d);
    return validDate(dt) ? dt.toLocaleDateString('pt-BR') : '—';
}

function formatDateTime(d) {
    const dt = parseDate(d);
    return validDate(dt) ? `${dt.toLocaleDateString('pt-BR')} ${dt.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}` : '—';
}

function formatDatePill(period) {
    const dt = period ? parseDate(period.start) : null;
    if (!validDate(dt)) return '—';
    const months = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ'];
    return `${dt.getDate()} ${months[dt.getMonth()]}`;
}

function isoDate(d) {
    if (typeof d === 'string') return d.slice(0, 10);
    const dt = parseDate(d);
    if (!validDate(dt)) return '';
    return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
}

function toLocalInput(d) {
    if (typeof d === 'string') return d.slice(0, 16);
    const dt = parseDate(d);
    return validDate(dt) ? `${isoDate(dt)}T${String(dt.getHours()).padStart(2, '0')}:${String(dt.getMinutes()).padStart(2, '0')}` : '';
}

function byStart(a, b) { return (parseDate(a.period.start) - parseDate(b.period.start)) || (a.id - b.id); }

function setText(id, text) { const el = $(id); if (el) el.textContent = text; }

function setMessage(id, text, kind) {
    const el = $(id);
    if (!el) return;
    el.textContent = text || '';
    el.className = 'inline-msg' + (kind ? ' ' + kind : '');
}

function safeStorage(action) { try { return action(); } catch (_) { return null; } }

// ---------------------------------------------------------------- notificações e modais
function notify(message, kind) {
    const region = $('toast-region');
    const toast = document.createElement('div');
    toast.className = 'toast ' + (kind || 'info');
    toast.textContent = message;
    region.appendChild(toast);
    while (region.children.length > 4) region.removeChild(region.firstChild);
    setTimeout(() => toast.remove(), 6500);
}

function openModal(id) {
    const modal = $(id);
    if (!modal) return;
    focusBeforeModal = document.activeElement;
    modal.classList.add('active');
    modal.setAttribute('aria-hidden', 'false');
    const first = modal.querySelector('input:not([type="hidden"]), select, textarea');
    (first || modal.querySelector('.modal-close')).focus();
}

function closeModal(id) {
    const modal = $(id);
    if (!modal) return;
    modal.classList.remove('active');
    modal.setAttribute('aria-hidden', 'true');
    if (id === 'modal-projector') stopQrTimer();
    if (focusBeforeModal && typeof focusBeforeModal.focus === 'function') focusBeforeModal.focus();
}

function activeModal() { return document.querySelector('.modal-overlay.active'); }

function onKeyDown(event) {
    if (event.key === 'Escape' && !$('event-picker-panel').hidden) {
        toggleEventPicker(false);
        $('event-picker-toggle').focus();
        return;
    }
    const modal = activeModal();
    if (!modal) return;
    if (event.key === 'Escape') {
        closeModal(modal.id);
        return;
    }
    if (event.key === 'Tab') {
        const focusables = [...modal.querySelectorAll('button, input, select, textarea, a[href]')]
            .filter(el => !el.disabled && el.offsetParent !== null);
        if (!focusables.length) return;
        const first = focusables[0];
        const last = focusables[focusables.length - 1];
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
}

// ---------------------------------------------------------------- API
class ApiError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}

async function api(path, options = {}) {
    const { method = 'GET', body, raw = false, handleSession = true } = options;
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (authToken) headers['Authorization'] = `Bearer ${authToken}`;
    let response;
    try {
        response = await fetch(path, {
            method,
            headers,
            body: body !== undefined ? JSON.stringify(body) : undefined
        });
    } catch (_) {
        throw new ApiError(0, 'Não foi possível se comunicar com o servidor.');
    }
    if (response.ok) {
        if (raw) return response;
        const text = await response.text();
        if (!text) return null;
        try { return JSON.parse(text); } catch (_) { return null; }
    }
    let data = null;
    try { data = JSON.parse(await response.text()); } catch (_) { data = null; }
    let message = (data && data.error) || `Erro ${response.status}`;
    if (response.status === 401 && authToken && handleSession) {
        sessionExpired();
        message = 'Sua sessão expirou. Entre novamente.';
    } else if (response.status === 403 && !(data && data.error)) {
        message = 'Você não tem permissão para esta operação.';
    }
    throw new ApiError(response.status, message);
}

function describeError(error) {
    if (!(error instanceof ApiError)) return 'Ocorreu um erro inesperado.';
    if (error.status === 409) return 'Conflito: ' + error.message;
    if (error.status === 403) return 'Permissão negada: ' + error.message;
    return error.message;
}

// ---------------------------------------------------------------- sessão
function persistSession(payload) {
    authToken = payload.token;
    activeUser = payload.user;
    safeStorage(() => {
        localStorage.setItem('eventos_token', authToken);
        localStorage.setItem('eventos_user', JSON.stringify(activeUser));
    });
}

function clearSession() {
    authToken = null;
    activeUser = GUEST;
    userRegs = [];
    eligibility = null;
    safeStorage(() => {
        localStorage.removeItem('eventos_token');
        localStorage.removeItem('eventos_user');
    });
}

function sessionExpired() {
    clearSession();
    updateUI();
    showTab('home');
    setMessage('auth-error', 'Sua sessão expirou. Entre novamente.', 'error');
    if (!activeModal()) { switchAuthTab('login'); openModal('modal-auth'); }
}

/** Abre o diálogo de acesso na aba pedida: 'login' (padrão) ou 'register'. */
function openAuthModal(mode) {
    const tab = mode === 'register' ? 'register' : 'login';
    setMessage('auth-error', '');
    setMessage('register-error', '');
    switchAuthTab(tab);
    openModal('modal-auth');
    const first = $(tab === 'register' ? 'register-name' : 'auth-email');
    if (first) first.focus();
}

function switchAuthTab(tab) {
    const register = tab === 'register';
    $('auth-panel-login').hidden = register;
    $('auth-panel-register').hidden = !register;
    const loginTab = $('auth-tab-login');
    const registerTab = $('auth-tab-register');
    loginTab.setAttribute('aria-selected', String(!register));
    registerTab.setAttribute('aria-selected', String(register));
    loginTab.tabIndex = register ? -1 : 0;
    registerTab.tabIndex = register ? 0 : -1;
    setText('auth-heading', register ? 'Criar sua conta' : 'Acesse o EventOS');
}

function togglePasswordVisibility(button) {
    const input = $(button.dataset.target);
    if (!input) return;
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    button.textContent = show ? 'Ocultar' : 'Mostrar';
    button.setAttribute('aria-label', show ? 'Ocultar senha' : 'Mostrar senha');
}

const MIN_PASSWORD_LENGTH = 6;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** Mostra, enquanto o usuário digita, se a confirmação de senha confere. */
function updateRegisterNotes() {
    const password = $('register-password').value;
    const confirm = $('register-password2').value;
    const note = $('register-match-note');
    if (!confirm) { note.textContent = ''; note.className = 'field-note'; return; }
    const matches = password === confirm;
    note.textContent = matches ? 'As senhas conferem.' : 'As senhas ainda não conferem.';
    note.className = 'field-note ' + (matches ? 'good' : 'bad');
}
document.addEventListener('input', event => {
    if (event.target && (event.target.id === 'register-password' || event.target.id === 'register-password2')) updateRegisterNotes();
});

async function afterAuthChange() {
    await loadEventsList();
    await loadData();
    updateUI();
    showTab('home');
}

async function login(emailOverride, passwordOverride) {
    const email = emailOverride || $('auth-email').value.trim();
    const password = passwordOverride || $('auth-password').value;
    if (!email || !password) {
        setMessage('auth-error', 'Informe e-mail e senha.', 'error');
        return;
    }
    try {
        const data = await api('/api/auth/login', { method: 'POST', body: { email, password }, handleSession: false });
        persistSession(data);
        $('auth-password').value = '';
        if ($('modal-auth').classList.contains('active')) closeModal('modal-auth');
        await afterAuthChange();
        notify(`Bem-vindo(a), ${activeUser.name}.`, 'success');
    } catch (error) {
        setMessage('auth-error', error.message, 'error');
        if (!$('modal-auth').classList.contains('active')) notify(error.message, 'error');
    }
}

async function registerAccount() {
    const name = $('register-name').value.trim().replace(/\s+/g, ' ');
    const email = $('register-email').value.trim();
    const password = $('register-password').value;
    const confirmation = $('register-password2').value;
    const problem = (message, fieldId) => {
        setMessage('register-error', message, 'error');
        $(fieldId).focus();
    };
    if (name.split(' ').length < 2 || name.length < 5) return problem('Informe seu nome completo (nome e sobrenome), como deve aparecer no certificado.', 'register-name');
    if (!EMAIL_PATTERN.test(email)) return problem('Informe um e-mail válido, por exemplo voce@exemplo.com.', 'register-email');
    if (password.length < MIN_PASSWORD_LENGTH) return problem(`A senha precisa ter ao menos ${MIN_PASSWORD_LENGTH} caracteres.`, 'register-password');
    if (password !== confirmation) return problem('A confirmação de senha não confere com a senha digitada.', 'register-password2');
    if (!$('register-terms').checked) return problem('Para criar a conta, aceite o uso dos seus dados descrito acima.', 'register-terms');
    try {
        const data = await api('/api/auth/register', { method: 'POST', body: { name, email, password }, handleSession: false });
        persistSession(data);
        ['register-name', 'register-email', 'register-password', 'register-password2'].forEach(id => { $(id).value = ''; });
        $('register-terms').checked = false;
        setMessage('register-error', '');
        closeModal('modal-auth');
        await afterAuthChange();
        notify('Conta criada. Escolha as atividades ou confirme sua inscrição no evento.', 'success');
    } catch (error) {
        setMessage('register-error', error.message, 'error');
    }
}

async function logout() {
    toggleAccountDropdown(false);
    if (authToken) {
        try { await api('/api/auth/logout', { method: 'POST', handleSession: false }); } catch (_) { /* sessão já inválida */ }
    }
    clearSession();
    await afterAuthChange();
}

async function restoreSession() {
    authToken = safeStorage(() => localStorage.getItem('eventos_token'));
    const stored = safeStorage(() => JSON.parse(localStorage.getItem('eventos_user') || 'null'));
    activeUser = stored || GUEST;
    if (!authToken) { activeUser = GUEST; return; }
    try {
        activeUser = await api('/api/auth/me', { handleSession: false });
        safeStorage(() => localStorage.setItem('eventos_user', JSON.stringify(activeUser)));
    } catch (_) {
        clearSession();
    }
}

// ---------------------------------------------------------------- contas de demonstração
async function loadDemoAccounts() {
    try {
        demoAccounts = (await api('/api/demo/accounts', { handleSession: false })) || [];
    } catch (_) {
        demoAccounts = []; // rota inexistente: modo de demonstração desligado
    }
    renderDemoAccounts();
}

function renderDemoAccounts() {
    const wrap = $('demo-accounts-wrap');
    wrap.hidden = !demoAccounts.length;
    if (!demoAccounts.length) return;
    const item = (account, index) => `
        <button type="button" class="demo-account${account.email === activeUser.email ? ' selected' : ''}" data-action="demoLogin" data-index="${index}">
            <span>${escapeHtml(account.name)}</span>
            <span class="role">${escapeHtml(ROLE_LABELS[account.role] || account.role)}</span>
        </button>`;
    const staff = demoAccounts.map((a, i) => [a, i]).filter(([a]) => a.role !== 'PARTICIPANT');
    const students = demoAccounts.map((a, i) => [a, i]).filter(([a]) => a.role === 'PARTICIPANT');
    $('demo-accounts-list').innerHTML =
        (staff.length ? '<div class="demo-group">Organização</div>' + staff.map(([a, i]) => item(a, i)).join('') : '') +
        (students.length ? '<div class="demo-group">Participantes</div>' + students.map(([a, i]) => item(a, i)).join('') : '');
}

function toggleAccountDropdown(force) {
    const dropdown = $('account-dropdown');
    const trigger = $('user-pill-trigger');
    const open = typeof force === 'boolean' ? force : !dropdown.classList.contains('open');
    dropdown.classList.toggle('open', open);
    trigger.classList.toggle('active', open);
    trigger.setAttribute('aria-expanded', String(open));
}

// ---------------------------------------------------------------- eventos e dados
const EVENT_PAGE_SIZE = 15;

/**
 * Lista de eventos com busca no servidor (nome e descrição), paginação e o filtro "Meus eventos". A mesma lógica serve
 * ao seletor da barra do evento e à tela de escolha da aba Programação; só mudam os elementos (ids) e o detalhe mostrado.
 */
function createEventList(ids, detailed) {
    const state = { items: [], total: 0, search: '', mine: false, seq: 0, timer: null };
    const list = { state };

    list.setScope = mine => {
        state.mine = mine;
        document.querySelectorAll(`#${ids.scope} .chip`).forEach(chip => {
            const on = (chip.dataset.scope === 'mine') === mine;
            chip.classList.toggle('is-on', on);
            chip.setAttribute('aria-pressed', String(on));
        });
    };

    list.clearSearch = () => {
        state.search = '';
        $(ids.search).value = '';
    };

    list.onSearchInput = () => {
        clearTimeout(state.timer);
        state.timer = setTimeout(() => {
            state.search = $(ids.search).value.trim();
            list.load(true);
        }, 250);
    };

    /** Busca uma página no servidor; o total vem no cabeçalho X-Total-Count. */
    list.load = async reset => {
        const seq = ++state.seq;
        const query = new URLSearchParams({ limit: EVENT_PAGE_SIZE, offset: reset ? 0 : state.items.length });
        if (state.search) query.set('search', state.search);
        if (state.mine) query.set('mine', 'true');
        try {
            const response = await api(`/api/events?${query}`, { raw: true });
            const page = (await response.json()) || [];
            if (seq !== state.seq) return; // resposta de uma busca antiga
            state.total = Number(response.headers.get('X-Total-Count')) || page.length;
            state.items = reset ? page : state.items.concat(page);
        } catch (error) {
            if (seq !== state.seq) return;
            state.items = [];
            state.total = 0;
            notify(describeError(error), 'error');
        }
        list.render();
    };

    list.render = () => {
        const { items, total, search } = state;
        const noEvents = isLoggedIn() && isStaff()
            ? '<button type="button" class="btn-action btn-primary" data-action="goTab" data-tab="admin">Criar um evento</button>' : '';
        $(ids.list).innerHTML = items.length ? items.map(e => {
            const status = e.status !== 'PUBLISHED'
                ? `<span class="tag-pill ${e.status === 'DRAFT' ? 'amber' : ''}">${escapeHtml(e.statusName)}</span>` : '';
            const description = detailed && e.description ? `<span class="event-option-desc">${escapeHtml(e.description)}</span>` : '';
            return `<li><button type="button" class="event-option" data-action="pickEvent" data-id="${toId(e.id)}"${e.id === currentEventId ? ' aria-current="true"' : ''}>
                    <span class="event-option-title">${escapeHtml(e.title)}</span>
                    <span class="event-option-meta"><span class="mono">${escapeHtml(formatDate(e.period.start))} a ${escapeHtml(formatDate(e.period.end))}</span>${status}</span>
                    ${description}
                </button></li>`;
        }).join('') : `<li class="event-picker-empty">${search
            ? `Nenhum evento encontrado para “${escapeHtml(search)}”.`
            : `Nenhum evento disponível no momento. ${detailed ? noEvents : ''}`}</li>`;
        setText(ids.count, total ? `${items.length} de ${total} ${total === 1 ? 'evento' : 'eventos'}` : '');
        $(ids.more).hidden = items.length >= total;
    };

    return list;
}

const eventPicker = createEventList({ search: 'event-search', list: 'event-picker-list', count: 'event-picker-count',
    more: 'event-picker-more', scope: 'event-picker-scope' }, false);
const eventChooser = createEventList({ search: 'choose-search', list: 'choose-list', count: 'choose-count',
    more: 'choose-more', scope: 'choose-scope' }, true);
const EVENT_LISTS = { picker: eventPicker, chooser: eventChooser };

/**
 * Mantém o evento escolhido nesta visita enquanto ele ainda existir e não estiver cancelado. Nada é lembrado entre
 * visitas: ao recarregar a página ninguém tem evento escolhido, e a aba Programação pede que se escolha um.
 */
async function loadEventsList() {
    safeStorage(() => localStorage.removeItem('eventos_event_id')); // escolha guardada por uma versão anterior
    if (!currentEventId) return;
    try {
        const current = await api(`/api/events/${currentEventId}`);
        if (current.status !== 'CANCELLED') return;
    } catch (_) {
        // sem acesso ou inexistente: segue sem esse evento
    }
    currentEventId = null;
}

function renderEventPickerCurrent() {
    const suffix = eventData && eventData.status !== 'PUBLISHED' ? ` (${eventData.statusName})` : '';
    setText('event-picker-current', eventData ? eventData.title + suffix : 'Nenhum evento selecionado');
    setText('event-picker-action', eventData ? 'Trocar evento' : 'Escolher evento');
    $('projector-event-name').value = eventData ? eventData.title : '';
}

function toggleEventPicker(open) {
    const panel = $('event-picker-panel');
    const show = open === undefined ? panel.hidden : open;
    panel.hidden = !show;
    $('event-picker-toggle').setAttribute('aria-expanded', String(show));
    if (!show) return;
    const staff = isLoggedIn() && isStaff();
    $('event-picker-scope').hidden = !staff;
    if (!staff && eventPicker.state.mine) eventPicker.setScope(false);
    // Cada abertura começa limpa: sem a busca da vez anterior.
    eventPicker.clearSearch();
    $('event-search').focus();
    eventPicker.load(true);
}

/** Na aba Programação: o evento escolhido ou, sem evento, a lista para escolher. */
function renderProgView() {
    const choosing = !eventData;
    $('prog-event').hidden = choosing;
    $('prog-choose').hidden = !choosing;
    $('choose-scope').hidden = !(isLoggedIn() && isStaff());
    syncEventbar();
}

/** A barra "Evento exibido" não faz sentido na página inicial nem enquanto a lista de escolha ocupa a tela. */
function syncEventbar() {
    $('eventbar').hidden = currentTab === 'home' || (currentTab === 'prog' && !eventData);
}

async function pickEvent(eventId) {
    const pickerWasOpen = !$('event-picker-panel').hidden;
    toggleEventPicker(false);
    if (pickerWasOpen) $('event-picker-toggle').focus();
    if (eventId === currentEventId) return;
    currentEventId = eventId;
    await loadData();
    updateUI();
    if (currentTab === 'admin') loadAdminPanel();
    if (currentTab === 'prog') window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function loadData() {
    eventData = null;
    activities = [];
    eligibility = null;
    if (currentEventId) {
        try {
            eventData = await api(`/api/events/${currentEventId}`);
            activities = (eventData.activities || []).slice().sort(byStart);
        } catch (error) {
            notify(describeError(error), 'error');
        }
    }
    userRegs = [];
    if (isLoggedIn()) {
        try { userRegs = (await api(`/api/registrations/user/${activeUser.id}`)) || []; } catch (_) { userRegs = []; }
        if (!isStaff() && eventData) {
            try { eligibility = await api(`/api/certificates/eligibility/${currentEventId}/${activeUser.id}`); } catch (_) { eligibility = null; }
        }
    }
    renderEventPickerCurrent();
    populateFilterOptions();
    populateActivitySelectors();
    await refreshProgram();
}

function confirmedRegistration() {
    return userRegs.find(r => r.eventId === currentEventId && r.status === 'CONFIRMED') || null;
}

function selectedActivityIds() {
    const reg = confirmedRegistration();
    return reg ? (reg.selectedActivityIds || []) : [];
}

function activityLabel(a) {
    return `${a.title} (${formatTime(a.period.start)} • ${a.location ? a.location.room : ''})`;
}

function populateActivitySelectors() {
    const options = activities.map(a => `<option value="${toId(a.id)}">${escapeHtml(activityLabel(a))}</option>`).join('')
        || '<option value="">Nenhuma atividade cadastrada</option>';
    ['projector-act-select', 'adm-manual-aid', 'sv-activity', 'res-activity'].forEach(id => {
        const select = $(id);
        const previous = select.value;
        select.innerHTML = options;
        if (previous && activities.some(a => String(a.id) === previous)) select.value = previous;
    });
}

// ---------------------------------------------------------------- interface geral
function updateUI() {
    renderProgView();
    renderHero();
    renderActivities();
    renderAgenda();
    renderCertWidget();
    updateSidebarAndRole();
    renderDemoAccounts();
}

function updateSidebarAndRole() {
    setText('sb-user-name', activeUser.name);
    setText('sb-user-role', ROLE_LABELS[activeUser.role] || 'Visitante');

    const logged = isLoggedIn();
    const staff = logged && isStaff();
    // Visitante vê "Entrar" e "Criar conta"; quem está logado vê o menu da conta.
    $('auth-actions').hidden = logged;
    $('user-pill-trigger').hidden = !logged;
    if (!logged) toggleAccountDropdown(false);

    $('nav-agenda').style.display = logged && !staff ? 'inline-flex' : 'none';
    $('nav-cert').style.display = logged ? 'inline-flex' : 'none';
    $('widget-teacher-box').style.display = staff && canManageEvent() ? 'block' : 'none';
    $('docente-section-title').style.display = staff ? 'block' : 'none';
    $('nav-admin').style.display = staff ? 'inline-flex' : 'none';
    $('nav-users').style.display = logged && isAdmin() ? 'inline-flex' : 'none';
}

function renderHero() {
    if (!eventData) {
        setText('hero-event-title', 'Nenhum evento disponível');
        setText('hero-event-desc', 'Quando houver eventos publicados, a programação aparecerá aqui.');
        setText('hero-date-range', '');
        setText('hero-status-badge', 'Sem evento');
        setText('hero-enrollment-badge', '');
        ['hero-meta-dates', 'hero-meta-rooms', 'hero-meta-rule', 'hero-meta-capacity'].forEach(id => setText(id, '—'));
        $('hero-register-wrap').innerHTML = isLoggedIn() && isStaff()
            ? '<button type="button" class="btn-action btn-primary" data-action="newEvent">Criar o primeiro evento</button>'
            : '';
        return;
    }
    setText('hero-event-title', eventData.title);
    setText('hero-event-desc', eventData.description || 'Sem descrição informada.');
    setText('hero-date-range', `${formatDate(eventData.period.start)} a ${formatDate(eventData.period.end)}`);
    const status = $('hero-status-badge');
    status.textContent = eventData.statusName;
    status.className = 'tag-pill ' + (eventData.enrollmentOpen ? 'blue' : 'amber');

    const totalHours = activities.reduce((sum, a) => sum + (a.period.durationHours || 0), 0);
    setText('hero-meta-dates', `${formatDate(eventData.period.start)} • ${totalHours.toFixed(1).replace('.', ',')} h de programação`);
    const rooms = [...new Set(activities.map(a => a.location && a.location.room).filter(Boolean))];
    setText('hero-meta-rooms', rooms.length ? rooms.slice(0, 2).join(', ') + (rooms.length > 2 ? ` +${rooms.length - 2}` : '') : '—');
    setText('hero-meta-rule', eventData.certificatePolicy ? eventData.certificatePolicy.policyDescription : '—');
    setText('hero-meta-capacity', `${eventData.maxCapacity} vagas • inscrição até ${formatDateTime(eventData.registrationDeadline)}`);

    const badge = $('hero-enrollment-badge');
    const reg = confirmedRegistration();
    let text;
    let css = 'tag-pill';
    if (!isLoggedIn()) text = 'Entre para se inscrever';
    else if (isStaff()) text = canManageEvent() ? 'Você organiza este evento' : 'Evento de outro organizador';
    else if (reg) { text = 'Você está inscrito'; css += ' green'; }
    else if (eventData.enrollmentOpen) text = 'Inscrições abertas';
    else { text = 'Inscrições encerradas'; css += ' amber'; }
    badge.textContent = text;
    badge.className = css;

    const wrap = $('hero-register-wrap');
    if (isLoggedIn() && !isStaff() && !reg && eventData.enrollmentOpen) {
        wrap.innerHTML = `<button type="button" class="btn-action btn-primary" data-action="registerEvent">Inscrever-se no evento</button>`
            + (eventData.activitySelectionRequired ? '<span class="hero-note">Este evento exige escolher ao menos uma atividade.</span>' : '');
    } else if (!isLoggedIn() && eventData.enrollmentOpen) {
        wrap.innerHTML = `<button type="button" class="btn-action btn-primary" onclick="openAuthModal('register')">Criar conta para se inscrever</button>
            <button type="button" class="btn-action btn-secondary" onclick="openAuthModal('login')">Já tenho conta</button>`;
    } else {
        wrap.innerHTML = '';
    }
}

function renderCertWidget() {
    const widget = $('cert-widget');
    if (!isLoggedIn() || isStaff() || !eligibility) { widget.style.display = 'none'; return; }
    widget.style.display = 'block';
    const percent = eligibility.totalHours > 0 ? Math.min(100, eligibility.hoursAttended / eligibility.totalHours * 100) : 0;
    $('cert-widget-bar').style.width = percent.toFixed(0) + '%';
    $('cert-widget-progress').setAttribute('aria-valuenow', percent.toFixed(0));
    setText('cert-widget-hours', eligibility.hoursAttended.toFixed(1).replace('.', ',') + ' h');
    setText('cert-widget-total', eligibility.totalHours.toFixed(1).replace('.', ',') + ' h');
    setText('cert-widget-rule', eligibility.policyDescription || '');
    const tag = $('cert-widget-tag');
    tag.textContent = eligibility.alreadyIssued ? 'Emitido' : (eligibility.eligible ? 'Elegível' : 'Em andamento');
    tag.className = 'tag-pill ' + (eligibility.eligible ? 'green' : 'amber');
}

// ---------------------------------------------------------------- filtros da programação
function readFilters() {
    return {
        search: $('filter-search').value.trim(),
        date: $('filter-date').value,
        from: $('filter-from').value,
        room: $('filter-room').value,
        track: $('filter-track').value,
        type: $('filter-type').value
    };
}

function hasActiveFilters(f) {
    return Boolean(f.search || f.date || f.from || f.room || f.track || f.type);
}

function populateFilterOptions() {
    const fill = (id, entries, allLabel) => {
        const select = $(id);
        const previous = select.value;
        select.innerHTML = `<option value="">${allLabel}</option>` +
            entries.map(([value, label]) => `<option value="${escapeHtml(value)}">${escapeHtml(label)}</option>`).join('');
        select.value = entries.some(([value]) => value === previous) ? previous : '';
    };
    const uniq = (list) => [...new Set(list.filter(Boolean))];
    fill('filter-date', uniq(activities.map(a => isoDate(a.period.start))).sort()
        .map(d => [d, formatDate(d + 'T00:00:00')]), 'Todas');
    fill('filter-room', uniq(activities.map(a => a.location && a.location.room)).sort().map(r => [r, r]), 'Todos');
    fill('filter-track', uniq(activities.map(a => a.location && a.location.spaceOrTrack)).sort().map(t => [t, t]), 'Todas');
    const types = new Map(activities.map(a => [a.type, a.typeName || a.type]));
    fill('filter-type', [...types.entries()], 'Todos');
    const startHours = activities.map(a => parseDate(a.period.start).getHours());
    const firstHour = Math.min(7, ...startHours);
    const lastHour = Math.max(21, ...startHours);
    const slots = [];
    for (let hour = firstHour; hour <= lastHour; hour++) {
        for (const minute of ['00', '30']) {
            const value = `${String(hour).padStart(2, '0')}:${minute}`;
            slots.push([value, value]);
        }
    }
    fill('filter-from', slots, 'Qualquer');
}

function onSearchInput() {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(refreshProgram, 300);
}

function clearFilters() {
    $('filter-search').value = '';
    ['filter-date', 'filter-room', 'filter-track', 'filter-type'].forEach(id => { $(id).value = ''; });
    $('filter-from').value = '';
    refreshProgram();
}

async function refreshProgram() {
    const sequence = ++programSeq;
    const filters = readFilters();
    $('btn-clear-filters').style.display = hasActiveFilters(filters) ? 'inline-flex' : 'none';
    if (!eventData) {
        programActivities = [];
    } else if (!hasActiveFilters(filters)) {
        programActivities = activities;
    } else {
        const query = new URLSearchParams();
        Object.entries(filters).forEach(([key, value]) => { if (value) query.set(key === 'search' ? 'search' : key, value); });
        try {
            const result = await api(`/api/events/${currentEventId}/activities?${query.toString()}`);
            if (sequence !== programSeq) return; // resposta de uma consulta antiga
            programActivities = result || [];
        } catch (error) {
            if (sequence !== programSeq) return;
            programActivities = [];
            notify(describeError(error), 'error');
        }
    }
    renderActivities();
}

// ---------------------------------------------------------------- programação
function speakersHtml(a) {
    const canIssueDeclaration = canManageEvent();
    return (a.speakers || []).map(s => {
        const declaration = canIssueDeclaration && s.id != null
            ? ` <button type="button" class="btn-action btn-secondary btn-sm" data-action="speakerDeclaration" data-activity="${toId(a.id)}" data-speaker="${toId(s.id)}"
                    title="Gerar declaração em PDF para ${escapeHtml(s.name)}">Declaração</button>`
            : '';
        return `<span><strong>${escapeHtml(s.name)}</strong>, ${escapeHtml((s.roleInActivity || 'Palestrante').toLowerCase())}${declaration}</span>`;
    }).join('');
}

function capacityText(a) {
    return a.requiresRegistration ? `${a.currentEnrollments || 0} / ${a.maxCapacity} vagas` : 'Sem controle de vagas';
}

/** Uma linha da grade horária: horário e sala à esquerda, conteúdo no centro, vagas e ação à direita. */
function activityCardHtml(a, actionHtml, mine) {
    const room = a.location ? a.location.room : '';
    const track = a.location ? (a.location.spaceOrTrack || 'Geral') : 'Geral';
    const policy = a.attendancePolicy ? a.attendancePolicy.policyName : '';
    const full = a.requiresRegistration && a.availableSlots <= 0;
    const speakers = speakersHtml(a);
    return `
        <article class="act-card${full ? ' is-full' : ''}${mine ? ' is-mine' : ''}">
            <div class="act-time-col">
                <span class="act-date-pill">${escapeHtml(formatDatePill(a.period))}</span>
                <span class="act-time-val mono">${escapeHtml(formatTime(a.period.start))}&ndash;${escapeHtml(formatTime(a.period.end))}</span>
                <span class="act-room-val">${escapeHtml(room)}</span>
            </div>
            <div class="act-body-col">
                <div class="act-tags">
                    <span class="tag-pill blue">${escapeHtml(track)}</span>
                    <span class="tag-pill">${escapeHtml(a.typeName || a.type)}</span>
                    ${policy ? `<span class="tag-pill">${escapeHtml(policy)}</span>` : ''}
                    ${full ? '<span class="tag-pill red">Lotada</span>' : ''}
                </div>
                <h3 class="act-title"><button type="button" class="act-title-link" data-action="openActivity" data-id="${toId(a.id)}">${escapeHtml(a.title)}</button></h3>
                <p class="act-desc">${escapeHtml(a.description)}</p>
                ${speakers ? `<div class="act-speakers"><span class="label">Com</span>${speakers}</div>` : ''}
            </div>
            <div class="act-action-col">
                <span class="capacity-val mono">${escapeHtml(capacityText(a))}</span>
                ${actionHtml}
                <button type="button" class="btn-clear-filters" data-action="openActivity" data-id="${toId(a.id)}">Ver detalhes</button>
            </div>
        </article>`;
}

/** O botão principal de uma atividade na programação, conforme o perfil e a situação da inscrição. */
function activityActionHtml(a, selected) {
    const id = toId(a.id);
    if (isStaff()) {
        if (!canManageEvent()) return '<span class="muted">Somente consulta</span>';
        return `<button type="button" class="btn-action btn-secondary btn-sm" data-action="projectActivity" data-id="${id}">Projetar QR Code</button>`;
    }
    if (selected.includes(a.id)) {
        return `<button type="button" class="btn-action btn-secondary btn-sm" data-action="removeFromAgenda" data-id="${id}" title="Remover da minha agenda">Na minha agenda &#10003;</button>`;
    }
    if (!eventData.activitySelectionEnabled) return '<span class="muted">Escolha de atividades desativada</span>';
    if (!eventData.enrollmentOpen) return '<button type="button" class="btn-action btn-secondary btn-sm" disabled>Inscrições encerradas</button>';
    if (a.requiresRegistration && a.availableSlots <= 0) return '<button type="button" class="btn-action btn-secondary btn-sm" disabled>Lotada</button>';
    return `<button type="button" class="btn-action btn-primary btn-sm" data-action="addToAgenda" data-id="${id}">Adicionar</button>`;
}

/** Janela com tudo o que se sabe sobre a atividade, com as ações que fazem sentido para quem está vendo. */
function openActivityDetail(activityId) {
    const a = activities.find(item => item.id === activityId);
    if (!a || !eventData) return;
    const id = toId(a.id);
    const selected = selectedActivityIds();
    const room = a.location ? a.location.room : '';
    const track = a.location ? (a.location.spaceOrTrack || 'Geral') : 'Geral';
    const full = a.requiresRegistration && a.availableSlots <= 0;
    const duration = a.period.durationHours ? `${a.period.durationHours.toFixed(1).replace('.', ',')} h` : '';
    const policy = a.attendancePolicy;
    const canDeclare = canManageEvent();

    const speakers = (a.speakers || []).map(s => `
        <li>
            <div><strong>${escapeHtml(s.name)}</strong> <span class="muted">${escapeHtml((s.roleInActivity || 'Palestrante').toLowerCase())}</span>
            ${canDeclare && s.id != null ? `<button type="button" class="btn-action btn-secondary btn-sm" data-action="speakerDeclaration" data-activity="${id}" data-speaker="${toId(s.id)}">Declaração</button>` : ''}</div>
            ${s.bio ? `<p>${escapeHtml(s.bio)}</p>` : ''}
        </li>`).join('');

    let actions = '';
    if (isStaff()) {
        actions = activityActionHtml(a, selected);
    } else if (selected.includes(a.id)) {
        actions = `${activityActionHtml(a, selected)}
            <button type="button" class="btn-action btn-primary btn-sm" data-action="openAttendance">Registrar presença</button>
            <button type="button" class="btn-action btn-secondary btn-sm" data-action="openFeedback" data-id="${id}">Avaliar</button>`;
    } else {
        actions = activityActionHtml(a, selected);
    }

    setText('activity-event', eventData.title);
    setText('activity-heading', a.title);
    $('activity-detail-body').innerHTML = `
        <div class="act-tags">
            <span class="tag-pill blue">${escapeHtml(track)}</span>
            <span class="tag-pill">${escapeHtml(a.typeName || a.type)}</span>
            ${full ? '<span class="tag-pill red">Lotada</span>' : ''}
        </div>
        <dl class="detail-facts">
            <div><dt>Data</dt><dd>${escapeHtml(formatDate(a.period.start))}</dd></div>
            <div><dt>Horário</dt><dd class="mono">${escapeHtml(formatTime(a.period.start))}&ndash;${escapeHtml(formatTime(a.period.end))}${duration ? `<span class="detail-sub">${escapeHtml(duration)} de duração</span>` : ''}</dd></div>
            <div><dt>Local</dt><dd>${escapeHtml(room || '—')}</dd></div>
            <div><dt>Vagas</dt><dd>${escapeHtml(capacityText(a))}</dd></div>
        </dl>
        <h4 class="modal-subtitle">Sobre a atividade</h4>
        <p class="detail-desc">${escapeHtml(a.description || 'Sem descrição informada.')}</p>
        ${speakers ? `<h4 class="modal-subtitle">Palestrantes e responsáveis</h4><ul class="detail-speakers">${speakers}</ul>` : ''}
        ${policy ? `<h4 class="modal-subtitle">Como a presença é registrada</h4>
            <p class="detail-desc"><strong>${escapeHtml(policy.policyName)}.</strong> ${escapeHtml(policy.policyDescription || '')}</p>` : ''}
        <div class="detail-actions">${actions}</div>`;
    openModal('modal-activity');
}

function renderActivities() {
    const wrap = $('activities-list-wrap');
    const total = programActivities.length;
    setText('activities-count-text', total === 1 ? '1 atividade' : `${total} atividades`);
    if (!eventData) {
        wrap.innerHTML = '<div class="empty-filter-state"><h4>Nenhum evento disponível</h4></div>';
        return;
    }
    if (programActivities.length === 0) {
        const filtered = hasActiveFilters(readFilters());
        wrap.innerHTML = `
            <div class="empty-filter-state">
                <h4>${filtered ? 'Nenhuma atividade com esses filtros' : 'Este evento ainda não tem atividades cadastradas'}</h4>
                ${filtered ? '<p>Remova algum filtro para ver a programação completa.</p><button type="button" class="btn-action btn-secondary" data-action="clearFilters">Limpar filtros</button>' : ''}
            </div>`;
        updateWidgetAgenda();
        return;
    }
    const selected = selectedActivityIds();
    wrap.innerHTML = programActivities
        .map(a => activityCardHtml(a, activityActionHtml(a, selected), !isStaff() && selected.includes(a.id)))
        .join('');
    updateWidgetAgenda();
}

// ---------------------------------------------------------------- agenda
function myActivities() {
    const ids = selectedActivityIds();
    return activities.filter(a => ids.includes(a.id)).sort(byStart);
}

function renderAgenda() {
    if (!eventData) {
        setText('sidebar-agenda-badge', '0');
        setText('widget-agenda-badge', '0');
        $('agenda-list-wrap').innerHTML = '<div class="empty-filter-state"><h4>Nenhum evento selecionado</h4><p>Escolha um evento para ver a sua agenda nele.</p><button type="button" class="btn-action btn-primary" onclick="showTab(\'prog\')">Escolher evento</button></div>';
        return;
    }
    const mine = myActivities();
    setText('sidebar-agenda-badge', String(mine.length));
    setText('widget-agenda-badge', String(mine.length));
    const wrap = $('agenda-list-wrap');
    if (mine.length === 0) {
        wrap.innerHTML = '<div class="empty-filter-state"><h4>Sua agenda está vazia</h4><p>Escolha atividades na programação para montá-la.</p><button type="button" class="btn-action btn-primary" onclick="showTab(\'prog\')">Ver programação</button></div>';
        return;
    }
    wrap.innerHTML = mine.map(a => activityCardHtml(a,
        `<button type="button" class="btn-action btn-primary btn-sm" data-action="openAttendance">Registrar presença</button>
         <button type="button" class="btn-action btn-secondary btn-sm" data-action="openFeedback" data-id="${toId(a.id)}">Avaliar</button>
         <button type="button" class="btn-action btn-danger btn-sm" data-action="removeFromAgenda" data-id="${toId(a.id)}">Remover</button>`,
        false)).join('');
}

function updateWidgetAgenda() {
    const container = $('widget-agenda-items');
    const mine = myActivities();
    if (mine.length === 0) {
        container.innerHTML = '<span class="muted">Nenhuma atividade escolhida ainda.</span>';
        return;
    }
    container.innerHTML = mine.slice(0, 4).map(a => `
        <div class="mini-item">
            <span class="mini-time">${escapeHtml(formatTime(a.period.start))}</span>
            <span class="mini-title">${escapeHtml(a.title)}</span>
        </div>`).join('');
}

async function addToAgenda(actId) {
    if (!isLoggedIn()) { openAuthModal(); return; }
    if (isStaff()) { notify('Perfis de organização não se inscrevem como participantes.', 'warning'); return; }
    try {
        const first = !confirmedRegistration();
        if (first) {
            await api('/api/registrations', { method: 'POST', body: { eventId: currentEventId, activityIds: [actId] } });
        } else {
            await api(`/api/registrations/${currentEventId}/activities/${actId}`, { method: 'POST', body: {} });
        }
        await loadData();
        updateUI();
        notify(first ? 'Inscrição no evento confirmada e atividade adicionada à agenda.' : 'Atividade adicionada à sua agenda.', 'success');
    } catch (error) {
        notify(describeError(error), 'error');
    }
}

async function registerEvent() {
    if (eventData && eventData.activitySelectionRequired) {
        notify('Este evento exige escolher ao menos uma atividade: use o botão "Adicionar" na programação.', 'warning');
        return;
    }
    try {
        await api('/api/registrations', { method: 'POST', body: { eventId: currentEventId, activityIds: [] } });
        await loadData();
        updateUI();
        notify('Inscrição no evento confirmada.', 'success');
    } catch (error) {
        notify(describeError(error), 'error');
    }
}

async function removeFromAgenda(actId) {
    try {
        await api(`/api/registrations/${currentEventId}/activities/${actId}`, { method: 'DELETE' });
        await loadData();
        updateUI();
        notify('Atividade removida da agenda.', 'success');
    } catch (error) {
        notify(describeError(error), 'error');
    }
}

async function cancelEventRegistration() {
    if (!isLoggedIn() || !confirmedRegistration()) {
        notify('Você não possui inscrição confirmada neste evento.', 'warning');
        return;
    }
    if (!confirm('Deseja cancelar sua inscrição neste evento? As vagas das atividades serão liberadas.')) return;
    try {
        const data = await api(`/api/registrations/${currentEventId}/cancel`, { method: 'POST', body: {} });
        await loadData();
        updateUI();
        notify((data && data.message) || 'Inscrição cancelada.', 'success');
    } catch (error) {
        notify(describeError(error), 'error');
    }
}

// ---------------------------------------------------------------- frequência
function handleAttendanceClick() {
    if (!isLoggedIn()) { openAuthModal(); return; }
    if (isStaff()) openTeacherProjectorModal();
    else openStudentScannerModal();
}

function openTeacherProjectorModal() {
    if (!canManageEvent()) { notify('A projeção do QR Code é restrita ao organizador responsável pelo evento.', 'warning'); return; }
    populateActivitySelectors();
    openModal('modal-projector');
    generateProjectorToken();
}

function stopQrTimer() {
    if (qrTimer) clearInterval(qrTimer);
    qrTimer = null;
}

function startQrTimer() {
    stopQrTimer();
    const tick = () => {
        const remaining = Math.max(0, Math.round((qrExpiresAt - Date.now()) / 1000));
        const mm = String(Math.floor(remaining / 60)).padStart(2, '0');
        const ss = String(remaining % 60).padStart(2, '0');
        setText('projector-validity', remaining > 0 ? `Válido por mais ${mm}:${ss}` : 'Expirado; gerando novo código...');
        if (remaining === 0) {
            stopQrTimer();
            if ($('modal-projector').classList.contains('active')) generateProjectorToken();
        }
    };
    tick();
    qrTimer = setInterval(tick, 1000);
}

async function generateProjectorToken() {
    const actId = toId($('projector-act-select').value);
    setMessage('projector-msg', '');
    stopQrTimer();
    if (!actId) {
        setText('projector-act-title', 'Nenhuma atividade selecionada');
        setMessage('projector-msg', 'Cadastre uma atividade neste evento para gerar o QR Code.', 'error');
        return;
    }
    const act = activities.find(a => a.id === actId);
    setText('projector-act-title', act ? act.title : '');
    try {
        const data = await api(`/api/attendance/qr/generate/${actId}`);
        lastProjectorToken = data.qrToken;
        if (QR_IMAGE_PATTERN.test(data.qrImage || '')) $('projector-qr-img').src = data.qrImage;
        setText('projector-token-text', `Conteúdo do QR Code: ${data.qrToken}`);
        qrExpiresAt = Date.now() + (data.validForSeconds || 0) * 1000;
        startQrTimer();
    } catch (error) {
        setMessage('projector-msg', describeError(error), 'error');
    }
}

function openStudentScannerModal() {
    $('student-scan-result').innerHTML = '';
    $('btn-fill-last-token').style.display = lastProjectorToken ? 'inline-flex' : 'none';
    openModal('modal-scanner');
}

function fillLastToken() {
    if (lastProjectorToken) $('student-token-input').value = lastProjectorToken;
}

async function submitStudentAttendance() {
    const result = $('student-scan-result');
    const token = $('student-token-input').value.trim();
    if (!token) {
        result.innerHTML = '<div class="callout danger">Cole o conteúdo do QR Code exibido no telão.</div>';
        return;
    }
    try {
        const data = await api('/api/attendance/qr/scan', { method: 'POST', body: { qrToken: token } });
        const type = data.record ? data.record.typeName : '';
        if (data.alreadyRecorded) {
            result.innerHTML = `<div class="callout warning"><strong>Presença já registrada.</strong> Nenhuma marcação duplicada foi criada. Situação atual: <strong>${escapeHtml(data.statusName)}</strong>.</div>`;
        } else {
            result.innerHTML = `<div class="callout success"><strong>Presença registrada</strong> (${escapeHtml(type)}). Situação atual: <strong>${escapeHtml(data.statusName)}</strong>.</div>`;
            $('student-token-input').value = '';
        }
    } catch (error) {
        result.innerHTML = `<div class="callout danger"><strong>Não foi possível registrar a presença.</strong> ${escapeHtml(describeError(error))}</div>`;
    }
}

// ---------------------------------------------------------------- certificados
async function loadCertificateTab() {
    await Promise.all([renderEligibility(), renderCertificates()]);
}

async function renderEligibility() {
    const wrap = $('cert-eligibility-wrap');
    if (!isLoggedIn()) { wrap.innerHTML = '<span class="muted">Entre para consultar sua elegibilidade.</span>'; return; }
    if (!currentEventId) { wrap.innerHTML = '<p class="muted">Nenhum evento selecionado. Escolha um evento para consultar sua elegibilidade.</p><button type="button" class="btn-action btn-secondary" onclick="showTab(\'prog\')">Escolher evento</button>'; return; }
    try {
        eligibility = await api(`/api/certificates/eligibility/${currentEventId}/${activeUser.id}`);
    } catch (error) {
        wrap.innerHTML = `<div class="callout danger">${escapeHtml(describeError(error))}</div>`;
        return;
    }
    const e = eligibility;
    const kind = e.eligible ? 'success' : 'warning';
    wrap.innerHTML = `
        <div class="callout ${kind}"><strong>${e.eligible ? 'Elegível' : 'Ainda não elegível'}.</strong> ${escapeHtml(e.justification)}</div>
        <p class="cert-note"><strong>Critério:</strong> ${escapeHtml(e.policyDescription)}</p>
        <p class="cert-note">
            Presença validada em ${e.activitiesAttended} de ${e.totalActivities} atividades:
            <span class="mono">${e.hoursAttended.toFixed(1).replace('.', ',')} h de ${e.totalHours.toFixed(1).replace('.', ',')} h</span>
        </p>
        <button type="button" class="btn-action btn-primary section-btn" onclick="issueCertificate()" ${e.eligible && !e.alreadyIssued ? '' : 'disabled'}>
            ${e.alreadyIssued ? 'Certificado já emitido' : 'Emitir certificado'}
        </button>`;
    renderCertWidget();
}

async function issueCertificate() {
    if (!isLoggedIn()) { openAuthModal(); return; }
    try {
        await api(`/api/certificates/issue/${currentEventId}/${activeUser.id}`, { method: 'POST' });
        notify('Certificado emitido. Você já pode baixar o PDF.', 'success');
        await loadCertificateTab();
    } catch (error) {
        notify(describeError(error), 'error');
        renderEligibility();
    }
}

/** Último resultado de envio por e-mail do certificado (RF-34). A falha não impede o download. */
function deliveryNotice(delivery) {
    if (!delivery) return '';
    const when = escapeHtml(formatDateTime(delivery.attemptedAt));
    return delivery.status === 'SENT'
        ? `<p class="cert-note" role="status">Enviado por e-mail para <strong>${escapeHtml(delivery.recipient)}</strong> em ${when}.</p>`
        : `<p class="cert-note bad" role="status">O último envio por e-mail falhou (${when}): ${escapeHtml(delivery.detail)} Baixe o PDF normalmente.</p>`;
}

async function sendCertificateEmail(id) {
    try {
        const delivery = await api(`/api/certificates/${id}/send`, { method: 'POST' });
        if (delivery.status === 'SENT') {
            notify('Certificado enviado para ' + delivery.recipient + '.', 'success');
        } else {
            notify('Não foi possível enviar o e-mail: ' + delivery.detail, 'warning');
        }
        await renderCertificates();
    } catch (error) {
        notify(describeError(error), 'error');
    }
}

async function renderCertificates() {
    const wrap = $('cert-list-wrap');
    if (!isLoggedIn()) { wrap.innerHTML = '<span class="muted">Entre para consultar seus certificados.</span>'; return; }
    try {
        const certs = (await api(`/api/certificates/user/${activeUser.id}`)) || [];
        if (certs.length === 0) {
            wrap.innerHTML = '<span class="muted">Nenhum certificado emitido para você ainda.</span>';
            return;
        }
        const histories = await Promise.all(certs.map(c =>
            api(`/api/certificates/${toId(c.id)}/deliveries`).catch(() => [])));
        wrap.innerHTML = certs.map((c, i) => `
            <div class="cert-card">
                <div class="cert-title">${escapeHtml(c.eventTitle)}</div>
                <p class="cert-meta">
                    Concedido a <strong>${escapeHtml(c.participantName)}</strong> &middot; ${Number(c.completionHours).toFixed(1).replace('.', ',')} h
                    &middot; emitido em ${escapeHtml(formatDate(c.issueDate))}
                </p>
                <div class="cert-code mono">Código ${escapeHtml(c.verificationCode)}</div>
                <div class="cert-actions">
                    <button type="button" data-action="downloadCertificate" data-id="${toId(c.id)}" class="btn-action btn-primary btn-sm">Baixar PDF</button>
                    <button type="button" data-action="sendCertificateEmail" data-id="${toId(c.id)}" class="btn-action btn-secondary btn-sm">Enviar por e-mail</button>
                </div>
                ${deliveryNotice((histories[i] || [])[0])}
            </div>`).join('');
    } catch (error) {
        wrap.innerHTML = `<div class="callout danger">${escapeHtml(describeError(error))}</div>`;
    }
}

async function verifyCertificate(inputId = 'cert-code-input', resultId = 'cert-verify-result') {
    const code = $(inputId).value.trim();
    const wrap = $(resultId);
    if (!code) { wrap.innerHTML = '<div class="callout warning">Informe o código de verificação.</div>'; return; }
    try {
        const data = await api(`/api/certificates/verify/${encodeURIComponent(code)}`, { handleSession: false });
        wrap.innerHTML = `
            <div class="callout success">
                <strong>Certificado autêntico.</strong><br>
                Titular: <strong>${escapeHtml(data.participantName)}</strong><br>
                Evento: <strong>${escapeHtml(data.eventTitle)}</strong> • ${Number(data.completionHours).toFixed(1).replace('.', ',')} h<br>
                Emitido em ${escapeHtml(formatDate(data.issueDate))} • Código ${escapeHtml(data.verificationCode)}
            </div>`;
    } catch (error) {
        wrap.innerHTML = error.status === 404
            ? '<div class="callout danger"><strong>Código inválido ou não localizado.</strong></div>'
            : `<div class="callout danger">${escapeHtml(describeError(error))}</div>`;
    }
}

async function downloadFile(path, filename) {
    try {
        const response = await api(path, { raw: true });
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
        notify(describeError(error), 'error');
    }
}

// ---------------------------------------------------------------- painel do organizador: gestão do evento
const EVENT_STATUS_HINT = {
    DRAFT: 'Rascunho: só organizadores veem este evento. Cadastre as atividades e publique para abrir as inscrições.',
    PUBLISHED: 'Publicado: visível para todos, com inscrições abertas até o prazo.',
    IN_PROGRESS: 'Em andamento: visível para todos, com inscrições abertas até o prazo.',
    FINISHED: 'Encerrado: sem novas inscrições. Relatórios e certificados continuam disponíveis.',
    CANCELLED: 'Cancelado: não aparece para os participantes.'
};

function renderEventManager() {
    const actions = $('adm-event-actions');
    const newButton = '<button type="button" class="btn-action btn-primary" data-action="newEvent">Novo evento</button>';
    if (!eventData) {
        setText('adm-event-heading', 'Eventos');
        setText('adm-event-status', '');
        $('adm-event-status').hidden = true;
        setText('adm-event-summary', 'Nenhum evento selecionado. Escolha um dos seus em "Escolher evento" (filtro "Meus eventos") ou crie um novo para cadastrar atividades e abrir inscrições.');
        actions.innerHTML = newButton;
        return;
    }
    const status = eventData.status;
    const tag = $('adm-event-status');
    tag.hidden = false;
    tag.textContent = eventData.statusName;
    tag.className = 'tag-pill ' + (status === 'DRAFT' ? 'amber' : status === 'CANCELLED' ? 'red' : status === 'FINISHED' ? '' : 'green');
    setText('adm-event-heading', eventData.title);
    if (!canManageEvent()) {
        tag.textContent = 'Somente consulta';
        tag.className = 'tag-pill';
        setText('adm-event-summary', 'Este evento pertence a outro organizador. Só o responsável ou um administrador pode alterá-lo; '
            + 'você pode consultar a programação. Escolha um evento seu em "Trocar evento > Meus eventos" ou crie um novo.');
        actions.innerHTML = newButton;
        return;
    }
    setText('adm-event-summary', EVENT_STATUS_HINT[status] || '');
    const open = status !== 'FINISHED' && status !== 'CANCELLED';
    actions.innerHTML = newButton
        + '<button type="button" class="btn-action btn-secondary" data-action="editEvent">Editar dados</button>'
        + (status === 'DRAFT' ? '<button type="button" class="btn-action btn-primary" data-action="eventLifecycle" data-step="publish">Publicar</button>' : '')
        + (status === 'PUBLISHED' || status === 'IN_PROGRESS' ? '<button type="button" class="btn-action btn-secondary" data-action="eventLifecycle" data-step="finish">Encerrar evento</button>' : '')
        + (open ? '<button type="button" class="btn-action btn-danger" data-action="eventLifecycle" data-step="cancel">Cancelar evento</button>' : '');
}

/** Abre o formulário vazio (novo evento) ou preenchido com o evento selecionado (edição). */
function openEventForm(editing) {
    editingEventId = editing && eventData ? eventData.id : null;
    setMessage('ev-error', '');
    setText('event-form-heading', editingEventId ? 'Editar evento' : 'Novo evento');
    setText('event-form-lead', editingEventId
        ? 'As alterações valem para todos os participantes assim que forem salvas.'
        : 'O evento nasce como rascunho: só os organizadores o veem até você publicar.');
    setText('ev-save', editingEventId ? 'Salvar alterações' : 'Criar evento');
    if (editingEventId) {
        $('ev-title').value = eventData.title || '';
        $('ev-desc').value = eventData.description || '';
        $('ev-start').value = toLocalInput(eventData.period.start);
        $('ev-end').value = toLocalInput(eventData.period.end);
        $('ev-cap').value = eventData.maxCapacity;
        $('ev-deadline').value = eventData.registrationDeadline ? toLocalInput(eventData.registrationDeadline) : '';
        $('ev-selection').checked = Boolean(eventData.activitySelectionEnabled);
        $('ev-required').checked = Boolean(eventData.activitySelectionRequired);
    } else {
        const day = new Date();
        day.setDate(day.getDate() + 1);
        const date = isoDate(day);
        $('ev-title').value = '';
        $('ev-desc').value = '';
        $('ev-start').value = `${date}T08:00`;
        $('ev-end').value = `${date}T18:00`;
        $('ev-cap').value = 100;
        $('ev-deadline').value = '';
        $('ev-selection').checked = true;
        $('ev-required').checked = false;
    }
    openModal('modal-event');
}

const withSeconds = value => (value && value.length === 16 ? value + ':00' : value);

async function saveEvent() {
    const title = $('ev-title').value.trim();
    const start = $('ev-start').value;
    const end = $('ev-end').value;
    const capacity = toId($('ev-cap').value);
    if (!title) { setMessage('ev-error', 'Informe o nome do evento.', 'error'); $('ev-title').focus(); return; }
    if (!start || !end) { setMessage('ev-error', 'Informe o início e o fim do evento.', 'error'); return; }
    if (end <= start) { setMessage('ev-error', 'O fim do evento deve ser depois do início.', 'error'); return; }
    if (!capacity || capacity < 1) { setMessage('ev-error', 'A capacidade deve ser de pelo menos 1 inscrito.', 'error'); return; }
    if ($('ev-required').checked && !$('ev-selection').checked) {
        setMessage('ev-error', 'Para exigir a escolha de atividades, permita que os participantes as escolham.', 'error');
        return;
    }
    const body = {
        title,
        description: $('ev-desc').value.trim(),
        start: withSeconds(start),
        end: withSeconds(end),
        maxCapacity: capacity,
        activitySelectionEnabled: $('ev-selection').checked,
        activitySelectionRequired: $('ev-required').checked
    };
    if ($('ev-deadline').value) body.registrationDeadline = withSeconds($('ev-deadline').value);
    const editing = editingEventId != null;
    try {
        const saved = await api(editing ? `/api/events/${editingEventId}` : '/api/events', { method: editing ? 'PUT' : 'POST', body });
        closeModal('modal-event');
        currentEventId = saved.id;
        await loadEventsList();
        await loadData();
        updateUI();
        showTab('admin');
        notify(editing ? 'Evento atualizado.' : 'Evento criado como rascunho. Cadastre as atividades e publique.', 'success');
    } catch (error) {
        setMessage('ev-error', describeError(error), 'error');
    }
}

const LIFECYCLE_TEXT = {
    publish: { done: 'Evento publicado. As inscrições estão abertas.' },
    finish: { ask: 'Encerrar o evento? Novas inscrições serão recusadas e a ação não pode ser desfeita.', done: 'Evento encerrado.' },
    cancel: { ask: 'Cancelar o evento? Ele deixa de aparecer para os participantes e a ação não pode ser desfeita.', done: 'Evento cancelado.' }
};

async function changeEventLifecycle(step) {
    const text = LIFECYCLE_TEXT[step];
    if (!text || !eventData) return;
    if (text.ask && !window.confirm(text.ask)) return;
    try {
        await api(`/api/events/${eventData.id}/${step}`, { method: 'POST', body: {} });
        await loadEventsList();
        await loadData();
        updateUI();
        renderEventManager();
        notify(text.done, 'success');
    } catch (error) {
        notify(describeError(error), 'error');
    }
}

// ---------------------------------------------------------------- painel do organizador
async function loadAdminPanel() {
    renderEventManager();
    $('adm-body').hidden = !canManageEvent();
    if (!eventData) {
        setMessage('adm-activity-msg', 'Selecione um evento para usar o painel.', 'error');
        return;
    }
    if (!canManageEvent()) return;
    populateActivitySelectors();
    if (!$('adm-start').value || adminDatesEventId !== eventData.id) {
        adminDatesEventId = eventData.id;
        const base = toLocalInput(eventData.period.start);
        $('adm-start').value = base;
        $('adm-end').value = base ? base.slice(0, 11) + String((parseInt(base.slice(11, 13), 10) + 1) % 24).padStart(2, '0') + base.slice(13) : '';
    }
    if (!$('sv-questions').children.length) addBuilderQuestion();
    renderPolicyForm();
    loadReportSummary();
    loadAttendanceTable();
    loadSurveyResults();
}

async function createAdminActivity() {
    const title = $('adm-title').value.trim();
    const start = $('adm-start').value;
    const end = $('adm-end').value;
    if (!title) { setMessage('adm-activity-msg', 'Informe o título da atividade.', 'error'); return; }
    if (!start || !end) { setMessage('adm-activity-msg', 'Informe início e fim da atividade.', 'error'); return; }
    const speakers = $('adm-speakers').value.split('\n').map(line => line.trim()).filter(Boolean).map(line => {
        const [name, role] = line.split('|').map(part => part.trim());
        return { name, role: role || 'Palestrante' };
    });
    try {
        await api(`/api/events/${currentEventId}/activities`, {
            method: 'POST',
            body: {
                title,
                description: $('adm-desc').value.trim(),
                room: $('adm-room').value.trim(),
                track: $('adm-track').value.trim(),
                start: start.length === 16 ? start + ':00' : start,
                end: end.length === 16 ? end + ':00' : end,
                capacity: toId($('adm-cap').value) || 1,
                type: $('adm-type').value,
                attendancePolicy: $('adm-policy').value,
                requiresRegistration: $('adm-requires').checked,
                speakers
            }
        });
        setMessage('adm-activity-msg', 'Atividade salva. Nenhum conflito de sala foi detectado.', 'success');
        notify('Atividade cadastrada.', 'success');
        $('adm-title').value = '';
        $('adm-desc').value = '';
        $('adm-speakers').value = '';
        await loadData();
        updateUI();
        loadAdminPanel();
    } catch (error) {
        const message = error.status === 409
            ? 'Conflito de sala: ' + error.message
            : describeError(error);
        setMessage('adm-activity-msg', message, 'error');
        notify(message, 'error');
    }
}

async function loadReportSummary() {
    const box = $('report-summary');
    try {
        const [enrolled, attendance] = await Promise.all([
            api(`/api/reports/enrolled/${currentEventId}`),
            api(`/api/reports/attendance/${currentEventId}`)
        ]);
        box.innerHTML = `
            <div>Inscrições confirmadas: <strong>${enrolled.totalEnrolled}</strong> de ${enrolled.eventCapacity} vagas
                • canceladas: <strong>${enrolled.totalCancelled}</strong></div>
            <div>Taxa geral de presença: <strong>${attendance.overallAttendanceRate.toFixed(1).replace('.', ',')}%</strong>
                (${attendance.activitySummaries.length} atividade(s))</div>`;
    } catch (error) {
        box.innerHTML = `<span class="inline-msg error">${escapeHtml(describeError(error))}</span>`;
    }
}

function downloadReport(dataset) {
    const kind = dataset.path === 'attendance' ? 'frequencia' : 'inscritos';
    downloadFile(`/api/reports/${dataset.path}/${currentEventId}/${dataset.ext}`, `relatorio_${kind}_${currentEventId}.${dataset.ext}`);
}

function originLabel(origin) {
    if (!origin || origin === '-') return '—';
    if (origin === 'QR_SCAN') return 'QR Code';
    if (origin.startsWith('MANUAL_BY_USER_')) return 'Manual (responsável #' + origin.substring('MANUAL_BY_USER_'.length) + ')';
    return origin;
}

async function loadAttendanceTable() {
    const wrap = $('adm-attendance-table');
    const actId = toId($('adm-manual-aid').value);
    setMessage('adm-attendance-msg', '');
    if (!actId) { wrap.innerHTML = '<div class="empty-filter-state"><p>Selecione uma atividade.</p></div>'; return; }
    try {
        const rows = (await api(`/api/attendance/activity/${actId}/overview`)) || [];
        if (!rows.length) {
            wrap.innerHTML = '<div class="empty-filter-state"><p>Nenhum participante inscrito nesta atividade.</p></div>';
            return;
        }
        wrap.innerHTML = `
            <table class="data-table">
                <caption class="sr-only">Participantes inscritos na atividade e situação de presença</caption>
                <thead><tr><th scope="col">Participante</th><th scope="col">Situação</th><th scope="col">Último registro</th><th scope="col">Origem</th><th scope="col">Ações</th></tr></thead>
                <tbody>${rows.map(r => `
                    <tr>
                        <td><strong>${escapeHtml(r.userName)}</strong><span class="sub">${escapeHtml(r.userEmail)}</span></td>
                        <td><span class="tag-pill ${r.status === 'PRESENT' ? 'green' : (r.status === 'PARTIAL' ? 'amber' : '')}">${escapeHtml(ATTENDANCE_LABELS[r.status] || r.status)}</span></td>
                        <td>${r.lastRecordTime ? escapeHtml(formatDateTime(r.lastRecordTime)) : '—'}</td>
                        <td>${escapeHtml(originLabel(r.recordedBy))}</td>
                        <td class="cell-actions">
                            <button type="button" class="btn-action btn-secondary btn-sm" data-action="manualAttend" data-user="${toId(r.userId)}">Lançar presença</button>
                            <button type="button" class="btn-action btn-danger btn-sm" data-action="revokeAttend" data-user="${toId(r.userId)}">Revogar</button>
                        </td>
                    </tr>`).join('')}
                </tbody>
            </table>`;
    } catch (error) {
        wrap.innerHTML = '';
        setMessage('adm-attendance-msg', describeError(error), 'error');
    }
}

function requireJustification() {
    const just = $('adm-manual-just').value.trim();
    if (!just) {
        setMessage('adm-attendance-msg', 'Informe a justificativa: ela é obrigatória e fica registrada junto com o seu usuário.', 'error');
        $('adm-manual-just').focus();
        return null;
    }
    return just;
}

async function manualAttend(userId) {
    const notes = requireJustification();
    if (!notes) return;
    try {
        await api('/api/attendance/manual', {
            method: 'POST',
            body: { activityId: toId($('adm-manual-aid').value), userId, type: 'MANUAL_ENTRY', notes }
        });
        setMessage('adm-attendance-msg', 'Presença manual registrada com autoria e horário.', 'success');
        loadAttendanceTable();
    } catch (error) {
        setMessage('adm-attendance-msg', describeError(error), 'error');
    }
}

async function revokeAttend(userId) {
    const notes = requireJustification();
    if (!notes) return;
    if (!confirm('Revogar a presença vigente deste participante? O histórico será preservado.')) return;
    try {
        await api('/api/attendance/revoke', {
            method: 'POST',
            body: { activityId: toId($('adm-manual-aid').value), userId, notes }
        });
        setMessage('adm-attendance-msg', 'Presença revogada. O histórico de marcações foi preservado.', 'success');
        loadAttendanceTable();
    } catch (error) {
        setMessage('adm-attendance-msg', describeError(error), 'error');
    }
}

// ---------------------------------------------------------------- política de certificado
function renderPolicyForm() {
    const policy = eventData.certificatePolicy;
    setText('pol-current', policy ? 'Política atual: ' + policy.policyDescription : '');
    $('pol-activities').innerHTML = activities.map(a => `
        <label class="check-row compact">
            <input type="checkbox" class="pol-activity" value="${toId(a.id)}" ${policy && (policy.activityIds || []).includes(a.id) ? 'checked' : ''}>
            <span>${escapeHtml(a.title)}</span>
        </label>`).join('') || '<span class="muted">Cadastre atividades primeiro.</span>';
    if (policy) {
        $('pol-type').value = policy.policyKey;
        if (policy.percentage) $('pol-percentage').value = policy.percentage;
        if (policy.minActivities) $('pol-count').value = policy.minActivities;
    }
    onPolicyTypeChange();
}

function onPolicyTypeChange() {
    const type = $('pol-type').value;
    $('pol-percentage-box').style.display = type === 'MIN_PERCENTAGE' ? 'block' : 'none';
    $('pol-count-box').style.display = type === 'MIN_ACTIVITIES' ? 'block' : 'none';
    $('pol-required-box').style.display = type === 'REQUIRED_ACTIVITIES' ? 'block' : 'none';
}

async function saveCertificatePolicy() {
    const type = $('pol-type').value;
    const body = { type };
    if (type === 'MIN_PERCENTAGE') body.percentage = Number($('pol-percentage').value);
    if (type === 'MIN_ACTIVITIES') body.minActivities = toId($('pol-count').value);
    if (type === 'REQUIRED_ACTIVITIES') {
        body.activityIds = [...document.querySelectorAll('.pol-activity:checked')].map(box => toId(box.value));
    }
    try {
        eventData = await api(`/api/events/${currentEventId}/certificate-policy`, { method: 'PUT', body });
        setMessage('pol-msg', 'Política atualizada.', 'success');
        renderPolicyForm();
        renderHero();
    } catch (error) {
        setMessage('pol-msg', describeError(error), 'error');
    }
}

// ---------------------------------------------------------------- questionários (organizador)
function addBuilderQuestion() {
    const idx = ++builderCounter;
    const block = document.createElement('div');
    block.className = 'sv-q';
    block.innerHTML = `
        <div class="sv-q-head"><span>Pergunta</span><button type="button" class="btn-clear-filters" data-action="removeQuestion">Remover pergunta</button></div>
        <label class="form-label" for="svq-text-${idx}">Enunciado</label>
        <input type="text" class="form-input sv-text" id="svq-text-${idx}">
        <label class="form-label" for="svq-type-${idx}">Tipo de resposta</label>
        <select class="form-select sv-type" id="svq-type-${idx}">
            <option value="RATING_SCALE">Escala numérica (1 a 5)</option>
            <option value="SINGLE_CHOICE">Escolha única</option>
            <option value="TEXT">Resposta textual</option>
        </select>
        <div class="sv-options-wrap" hidden>
            <label class="form-label" for="svq-options-${idx}">Opções (uma por linha, no mínimo duas)</label>
            <textarea class="form-textarea sv-options" id="svq-options-${idx}" rows="3"></textarea>
        </div>
        <label class="check-row" for="svq-required-${idx}"><input type="checkbox" class="sv-required" id="svq-required-${idx}" checked> <span>Resposta obrigatória</span></label>`;
    $('sv-questions').appendChild(block);
}

async function createSurvey() {
    const activityId = toId($('sv-activity').value);
    const title = $('sv-title').value.trim();
    if (!activityId) { setMessage('sv-msg', 'Selecione a atividade avaliada.', 'error'); return; }
    if (!title) { setMessage('sv-msg', 'Informe o título do questionário.', 'error'); return; }
    const questions = [...document.querySelectorAll('#sv-questions .sv-q')].map(block => ({
        questionText: block.querySelector('.sv-text').value.trim(),
        type: block.querySelector('.sv-type').value,
        options: block.querySelector('.sv-options').value.split('\n').map(o => o.trim()).filter(Boolean),
        required: block.querySelector('.sv-required').checked
    }));
    if (!questions.length) { setMessage('sv-msg', 'Adicione ao menos uma pergunta.', 'error'); return; }
    if (questions.some(q => !q.questionText)) { setMessage('sv-msg', 'Todas as perguntas precisam de enunciado.', 'error'); return; }
    try {
        await api('/api/surveys', {
            method: 'POST',
            body: { activityId, title, anonymous: $('sv-anonymous').checked, questions }
        });
        setMessage('sv-msg', 'Questionário criado.', 'success');
        notify('Questionário criado.', 'success');
        $('sv-title').value = '';
        $('sv-questions').innerHTML = '';
        addBuilderQuestion();
        $('res-activity').value = String(activityId);
        loadSurveyResults();
    } catch (error) {
        setMessage('sv-msg', describeError(error), 'error');
    }
}

function distributionHtml(distribution) {
    const entries = Object.entries(distribution || {});
    const total = entries.reduce((sum, [, count]) => sum + count, 0);
    return entries.map(([label, count]) => `
        <div class="bar-row">
            <span class="bar-label" title="${escapeHtml(label)}">${escapeHtml(label)}</span>
            <span class="bar-track"><span class="bar-fill" style="display:block; width:${total ? (count / total * 100).toFixed(0) : 0}%;"></span></span>
            <span class="mono">${count}</span>
        </div>`).join('');
}

async function loadSurveyResults() {
    const body = $('res-body');
    const actId = toId($('res-activity').value);
    if (!actId) { body.innerHTML = ''; return; }
    try {
        const r = await api(`/api/surveys/activity/${actId}/results`);
        if (!r.questionMetrics || r.questionMetrics.length === 0) {
            body.innerHTML = '<div class="callout warning section-btn">Esta atividade ainda não possui questionário configurado.</div>';
            return;
        }
        body.innerHTML = `
            <div class="callout section-btn"><strong>${escapeHtml(r.surveyTitle)}</strong><br>
                ${escapeHtml(r.identificationNotice)}<br>
                Respostas: <strong>${r.totalResponses}</strong> &middot; média geral das escalas: <strong>${r.averageRating.toFixed(2).replace('.', ',')}</strong></div>
            ${r.questionMetrics.map(m => `
                <div class="result-block">
                    <div class="result-title">${escapeHtml(m.questionText)}</div>
                    <div class="result-meta">${m.answeredCount} resposta(s)${m.averageScore > 0 ? ' &middot; média ' + m.averageScore.toFixed(2).replace('.', ',') : ''}</div>
                    ${distributionHtml(m.optionDistribution)}
                </div>`).join('')}
            ${(r.textComments || []).length ? `<div class="result-block"><div class="result-title">Comentários</div>
                ${r.textComments.map(c => `<div class="comment-item">${escapeHtml(c)}</div>`).join('')}</div>` : ''}`;
    } catch (error) {
        body.innerHTML = `<div class="callout danger section-btn">${escapeHtml(describeError(error))}</div>`;
    }
}

// ---------------------------------------------------------------- usuários (administrador)
async function loadUsers() {
    const wrap = $('users-table');
    setMessage('users-msg', '');
    try {
        const users = (await api('/api/users')) || [];
        wrap.innerHTML = `
            <table class="data-table">
                <caption class="sr-only">Usuários cadastrados e seus perfis</caption>
                <thead><tr><th scope="col">Nome</th><th scope="col">E-mail</th><th scope="col">Perfil</th><th scope="col">Ação</th></tr></thead>
                <tbody>${users.map(u => `
                    <tr>
                        <td><strong>${escapeHtml(u.name)}</strong></td>
                        <td>${escapeHtml(u.email)}</td>
                        <td>
                            <label class="sr-only" for="role-sel-${toId(u.id)}">Perfil de ${escapeHtml(u.name)}</label>
                            <select class="select-filter" id="role-sel-${toId(u.id)}">
                                ${['PARTICIPANT', 'ORGANIZER', 'ADMIN'].map(r => `<option value="${r}" ${u.role === r ? 'selected' : ''}>${escapeHtml(ROLE_LABELS[r])}</option>`).join('')}
                            </select>
                        </td>
                        <td><button type="button" class="btn-action btn-secondary btn-sm" data-action="saveUserRole" data-id="${toId(u.id)}">Salvar perfil</button></td>
                    </tr>`).join('')}
                </tbody>
            </table>`;
    } catch (error) {
        wrap.innerHTML = '';
        setMessage('users-msg', describeError(error), 'error');
    }
}

async function saveUserRole(userId) {
    const role = $(`role-sel-${userId}`).value;
    try {
        await api(`/api/users/${userId}/role`, { method: 'PUT', body: { role } });
        setMessage('users-msg', 'Perfil atualizado.', 'success');
        if (userId === activeUser.id) {
            activeUser = await api('/api/auth/me');
            safeStorage(() => localStorage.setItem('eventos_user', JSON.stringify(activeUser)));
            updateUI();
            if (!isAdmin()) showTab('prog');
        }
    } catch (error) {
        setMessage('users-msg', describeError(error), 'error');
        loadUsers();
    }
}

async function createUser() {
    const body = {
        name: $('nu-name').value.trim(),
        email: $('nu-email').value.trim(),
        password: $('nu-password').value,
        role: $('nu-role').value
    };
    if (!body.name || !body.email || !body.password) { setMessage('nu-msg', 'Preencha nome, e-mail e senha.', 'error'); return; }
    try {
        const created = await api('/api/users', { method: 'POST', body });
        setMessage('nu-msg', `Usuário ${created.name} criado como ${ROLE_LABELS[created.role] || created.role}.`, 'success');
        ['nu-name', 'nu-email', 'nu-password'].forEach(id => { $(id).value = ''; });
        loadUsers();
    } catch (error) {
        setMessage('nu-msg', describeError(error), 'error');
    }
}

// ---------------------------------------------------------------- perfil
function openProfileModal() {
    if (!isLoggedIn()) { openAuthModal(); return; }
    setText('modal-user-name', activeUser.name);
    setText('modal-user-email', activeUser.email);
    setText('modal-user-role', ROLE_LABELS[activeUser.role] || activeUser.role);
    $('profile-name-input').value = activeUser.name;
    ['pwd-current', 'pwd-new', 'pwd-confirm'].forEach(id => { $(id).value = ''; });
    setMessage('profile-message', '');
    openModal('modal-profile');
}

async function saveProfileName() {
    const name = $('profile-name-input').value.trim();
    if (!name) { setMessage('profile-message', 'O nome não pode ficar vazio.', 'error'); return; }
    try {
        activeUser = await api(`/api/users/${activeUser.id}`, { method: 'PUT', body: { name } });
        safeStorage(() => localStorage.setItem('eventos_user', JSON.stringify(activeUser)));
        updateSidebarAndRole();
        setText('modal-user-name', activeUser.name);
        setMessage('profile-message', 'Nome atualizado.', 'success');
    } catch (error) {
        setMessage('profile-message', describeError(error), 'error');
    }
}

async function changePassword() {
    const current = $('pwd-current').value;
    const next = $('pwd-new').value;
    const confirmation = $('pwd-confirm').value;
    if (!current || !next) { setMessage('profile-message', 'Informe a senha atual e a nova senha.', 'error'); return; }
    if (next.length < 6) { setMessage('profile-message', 'A nova senha deve ter ao menos 6 caracteres.', 'error'); return; }
    if (next !== confirmation) { setMessage('profile-message', 'A confirmação não confere com a nova senha.', 'error'); return; }
    try {
        // 401 aqui significa "senha atual incorreta", não sessão expirada.
        await api(`/api/users/${activeUser.id}/password`, {
            method: 'PUT', body: { currentPassword: current, newPassword: next }, handleSession: false
        });
        ['pwd-current', 'pwd-new', 'pwd-confirm'].forEach(id => { $(id).value = ''; });
        setMessage('profile-message', 'Senha alterada com sucesso.', 'success');
    } catch (error) {
        setMessage('profile-message', describeError(error), 'error');
    }
}

// ---------------------------------------------------------------- avaliação (participante)
async function openFeedbackModal(activityId) {
    if (!isLoggedIn()) { openAuthModal(); return; }
    if (isStaff()) { notify('Organizadores consultam os resultados no Painel do Organizador.', 'warning'); return; }
    const pool = myActivities().length ? myActivities() : activities;
    if (!pool.length) { notify('Não há atividade disponível para avaliação neste evento.', 'warning'); return; }
    $('feedback-activity').innerHTML = pool.map(a => `<option value="${toId(a.id)}">${escapeHtml(a.title)}</option>`).join('');
    const chosen = pool.some(a => a.id === activityId) ? activityId : pool[0].id;
    $('feedback-activity').value = String(chosen);
    openModal('modal-feedback');
    await loadFeedbackForm(chosen);
}

function questionFieldHtml(question) {
    const id = `fq-${toId(question.id)}`;
    const required = question.required ? 'required aria-required="true"' : '';
    const label = `<label class="form-label" for="${id}">${escapeHtml(question.questionText)}${question.required ? ' <span aria-hidden="true">*</span>' : ''}</label>`;
    if (question.type === 'RATING_SCALE') {
        return label + `<select class="form-select feedback-answer" id="${id}" data-question-id="${toId(question.id)}" ${required}>
            <option value="">Selecione uma nota</option>
            <option value="5">5 - Excelente</option><option value="4">4 - Muito bom</option>
            <option value="3">3 - Regular</option><option value="2">2 - Insatisfatório</option>
            <option value="1">1 - Ruim</option></select>`;
    }
    if (question.type === 'SINGLE_CHOICE') {
        return label + `<select class="form-select feedback-answer" id="${id}" data-question-id="${toId(question.id)}" ${required}>
            <option value="">Selecione uma opção</option>
            ${(question.options || []).map(o => `<option value="${escapeHtml(o)}">${escapeHtml(o)}</option>`).join('')}</select>`;
    }
    return label + `<textarea class="form-textarea feedback-answer" id="${id}" data-question-id="${toId(question.id)}" rows="3" ${required}></textarea>`;
}

async function loadFeedbackForm(activityId) {
    const body = $('feedback-body');
    const submit = $('feedback-submit');
    currentFeedbackSurvey = null;
    submit.style.display = 'none';
    setMessage('feedback-error', '');
    body.innerHTML = '<p class="muted section-btn">Verificando elegibilidade…</p>';
    try {
        const eligibilityResult = await api(`/api/surveys/activity/${activityId}/can-evaluate/${activeUser.id}`);
        if (!eligibilityResult.canEvaluate) {
            body.innerHTML = `<div class="callout warning"><strong>Você não pode avaliar esta atividade agora.</strong><br>${escapeHtml(eligibilityResult.reason || 'Requisitos de avaliação não atendidos.')}</div>`;
            return;
        }
        const survey = await api(`/api/surveys/activity/${activityId}`);
        currentFeedbackSurvey = survey;
        body.innerHTML = `
            <div class="callout" role="note"><strong>Antes de enviar:</strong> ${escapeHtml(survey.identificationNotice)}</div>
            <h4 class="modal-subtitle">${escapeHtml(survey.title)}</h4>
            ${(survey.questions || []).map(questionFieldHtml).join('')}`;
        submit.style.display = 'inline-flex';
    } catch (error) {
        body.innerHTML = `<div class="callout danger">${escapeHtml(describeError(error))}</div>`;
    }
}

async function submitFeedback() {
    if (!currentFeedbackSurvey) return;
    const answers = {};
    for (const field of document.querySelectorAll('#feedback-body .feedback-answer')) {
        const value = field.value.trim();
        if (field.required && !value) {
            setMessage('feedback-error', 'Responda todas as perguntas obrigatórias (*).', 'error');
            field.focus();
            return;
        }
        answers[field.dataset.questionId] = value;
    }
    try {
        await api('/api/surveys/submit', {
            method: 'POST',
            body: { activityId: currentFeedbackSurvey.activityId, answers }
        });
        closeModal('modal-feedback');
        notify('Avaliação enviada. Obrigado pelo feedback!', 'success');
    } catch (error) {
        setMessage('feedback-error', describeError(error), 'error');
    }
}

// ---------------------------------------------------------------- página inicial
const BANNER_INTERVAL_MS = 8000;
const homeState = { data: null, slide: 0, timer: null, hovering: false, seq: 0 };

async function loadHome() {
    const seq = ++homeState.seq;
    try {
        homeState.data = await api('/api/home');
    } catch (error) {
        homeState.data = null;
        notify(describeError(error), 'error');
    }
    if (seq === homeState.seq) renderHome();
}

const dayOf = d => String(parseDate(d).getDate()).padStart(2, '0');
const monthShort = d => parseDate(d).toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '').toUpperCase();
const pluralize = (n, one, many) => `${n} ${n === 1 ? one : many}`;

function eventPhase(e) {
    if (!e.upcoming) return 'Realizado';
    return parseDate(e.period.start) <= new Date() ? 'Em andamento' : 'Próximo';
}

/** A inscrição só está aberta se o evento a aceita e o prazo ainda não passou. */
function registrationOpenNow(e) {
    return e.enrollmentOpen && (!e.registrationDeadline || parseDate(e.registrationDeadline) >= new Date());
}

function renderHome() {
    const data = homeState.data || { publicEvents: 0, upcomingEvents: 0, events: [], activities: [], speakers: [] };
    renderBanner(data);
    renderHomeStats(data);
    renderHomeEvents(data);
    renderHomeActivities(data);
    renderHomeSpeakers(data);
    renderHomeNotices(data);
    renderHomeCta();
}

// ---- banner
function bannerIntroActions() {
    const browse = '<button type="button" class="btn-action btn-ghost-light" data-action="browseEvents">Ver eventos</button>';
    if (!isLoggedIn()) return '<button type="button" class="btn-action btn-solid-light" data-action="openRegister">Criar conta</button>' + browse;
    if (isStaff()) return '<button type="button" class="btn-action btn-solid-light" data-action="goTab" data-tab="admin">Painel do organizador</button>' + browse;
    return '<button type="button" class="btn-action btn-solid-light" data-action="goTab" data-tab="agenda">Minha agenda</button>' + browse;
}

function bannerEventSlide(e, position, total) {
    const registration = registrationOpenNow(e)
        ? `Inscrições abertas até ${formatDate(e.registrationDeadline)}` : 'Inscrições encerradas';
    const facts = [`${formatDate(e.period.start)} a ${formatDate(e.period.end)}`, pluralize(e.activityCount, 'atividade', 'atividades')];
    if (e.tracks.length) facts.push(e.tracks.slice(0, 3).join(' · '));
    const signUp = !isLoggedIn() && registrationOpenNow(e)
        ? '<button type="button" class="btn-action btn-ghost-light" data-action="openRegister">Criar conta para se inscrever</button>' : '';
    return `
        <article class="banner-slide" role="group" aria-roledescription="slide" aria-label="${position} de ${total}">
            <div class="banner-copy">
                <p class="banner-kicker">${escapeHtml(eventPhase(e))} &middot; ${escapeHtml(registration)}</p>
                <h2 class="banner-title">${escapeHtml(e.title)}</h2>
                <p class="banner-desc">${escapeHtml(e.description)}</p>
                <p class="banner-facts mono">${facts.map(f => `<span>${escapeHtml(f)}</span>`).join('')}</p>
                <div class="banner-actions">
                    <button type="button" class="btn-action btn-solid-light" data-action="homeEvent" data-id="${toId(e.id)}">Ver programação</button>
                    ${signUp}
                </div>
            </div>
            <div class="banner-art banner-date" aria-hidden="true">
                <span class="bd-day">${dayOf(e.period.start)}</span>
                <span class="bd-month">${monthShort(e.period.start)}</span>
                <span class="bd-year">${parseDate(e.period.start).getFullYear()}</span>
            </div>
        </article>`;
}

function renderBanner(data) {
    const events = data.events.slice(0, 3);
    const total = events.length + 1;
    const intro = `
        <article class="banner-slide" role="group" aria-roledescription="slide" aria-label="1 de ${total}">
            <div class="banner-copy">
                <p class="banner-kicker">Plataforma de gestão de eventos</p>
                <h2 class="banner-title">Inscrição, programação, presença e certificado em um só lugar</h2>
                <p class="banner-desc">Acompanhe os eventos publicados, monte sua agenda, registre presença por QR Code e emita seu certificado com código de verificação.</p>
                <div class="banner-actions">${bannerIntroActions()}</div>
            </div>
            <div class="banner-art" aria-hidden="true"><img class="banner-mark" src="logo-mark-white.svg" alt="" width="150" height="150"></div>
        </article>`;
    $('banner-track').innerHTML = intro + events.map((e, i) => bannerEventSlide(e, i + 2, total)).join('');
    $('banner-dots').innerHTML = Array.from({ length: total }, (_, i) =>
        `<button type="button" class="banner-dot" data-action="bannerGo" data-index="${i}" aria-label="Ir para o destaque ${i + 1}" aria-current="false"></button>`).join('');
    $('home-banner').classList.toggle('is-single', total < 2);
    showBannerSlide(Math.min(homeState.slide, total - 1));
    syncBannerTimer();
}

function showBannerSlide(index) {
    const slides = document.querySelectorAll('#banner-track .banner-slide');
    if (!slides.length) return;
    homeState.slide = (index + slides.length) % slides.length;
    slides.forEach((slide, i) => {
        const on = i === homeState.slide;
        slide.classList.toggle('is-active', on);
        slide.setAttribute('aria-hidden', String(!on));
    });
    document.querySelectorAll('#banner-dots .banner-dot').forEach((dot, i) => {
        dot.setAttribute('aria-current', String(i === homeState.slide));
    });
}

/** Gira sozinho só na página inicial, enquanto o mouse ou o foco não estão no banner e se o usuário aceita animação. */
function syncBannerTimer() {
    clearInterval(homeState.timer);
    homeState.timer = null;
    const reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const slides = document.querySelectorAll('#banner-track .banner-slide').length;
    if (currentTab !== 'home' || homeState.hovering || reduceMotion || slides < 2) return;
    homeState.timer = setInterval(() => showBannerSlide(homeState.slide + 1), BANNER_INTERVAL_MS);
}

// ---- seções
function renderHomeStats(data) {
    const box = $('home-stats');
    if (!data.publicEvents) { box.innerHTML = ''; return; }
    const activitiesCount = data.events.reduce((sum, e) => sum + e.activityCount, 0);
    const stat = (value, label) => `<div class="stat"><strong class="mono">${value}</strong><span>${label}</span></div>`;
    box.innerHTML = stat(data.publicEvents, data.publicEvents === 1 ? 'evento publicado' : 'eventos publicados')
        + stat(data.upcomingEvents, data.upcomingEvents === 1 ? 'próximo ou em andamento' : 'próximos ou em andamento')
        + stat(activitiesCount, 'atividades nos eventos em destaque');
}

function homeEventCard(e) {
    const open = registrationOpenNow(e);
    const id = toId(e.id);
    return `
        <article class="event-card">
            <div class="event-card-top">
                <div class="date-block" aria-hidden="true"><span class="db-day">${dayOf(e.period.start)}</span><span class="db-month">${monthShort(e.period.start)}</span></div>
                <div class="act-tags">
                    <span class="tag-pill blue">${escapeHtml(eventPhase(e))}</span>
                    <span class="tag-pill ${open ? 'green' : 'amber'}">${open ? 'Inscrições abertas' : 'Inscrições encerradas'}</span>
                </div>
            </div>
            <h3 class="event-card-title"><button type="button" class="act-title-link" data-action="homeEvent" data-id="${id}">${escapeHtml(e.title)}</button></h3>
            <p class="event-card-desc">${escapeHtml(e.description)}</p>
            <p class="event-card-meta mono">${escapeHtml(formatDate(e.period.start))} a ${escapeHtml(formatDate(e.period.end))} &middot; ${escapeHtml(pluralize(e.activityCount, 'atividade', 'atividades'))}</p>
            <button type="button" class="btn-action btn-primary btn-sm" data-action="homeEvent" data-id="${id}">Ver programação</button>
        </article>`;
}

function renderHomeEvents(data) {
    const box = $('home-events');
    if (!data.events.length) {
        const create = isLoggedIn() && isStaff()
            ? '<button type="button" class="btn-action btn-primary" data-action="goTab" data-tab="admin">Criar o primeiro evento</button>' : '';
        box.innerHTML = `<div class="empty-filter-state"><h4>Ainda não há eventos publicados</h4><p>Quando um organizador publicar um evento, ele aparece aqui.</p>${create}</div>`;
        return;
    }
    box.innerHTML = data.events.map(homeEventCard).join('');
}

function renderHomeActivities(data) {
    const box = $('home-activities');
    if (!data.activities.length) { box.innerHTML = '<div class="empty-filter-state"><h4>A programação aparece aqui</h4></div>'; return; }
    box.innerHTML = data.activities.map(a => `
        <button type="button" class="home-act" data-action="homeActivity" data-event="${toId(a.eventId)}" data-id="${toId(a.id)}">
            <span class="home-act-when">
                <span class="act-date-pill">${escapeHtml(formatDatePill(a.period))}</span>
                <span class="mono">${escapeHtml(formatTime(a.period.start))}&ndash;${escapeHtml(formatTime(a.period.end))}</span>
            </span>
            <span class="home-act-main">
                <span class="home-act-title">${escapeHtml(a.title)}</span>
                <span class="home-act-meta">${escapeHtml([a.eventTitle, a.room].filter(Boolean).join(' · '))}${a.speakers.length ? ' &middot; ' + escapeHtml(a.speakers.join(', ')) : ''}</span>
            </span>
        </button>`).join('');
}

const NAME_TITLES = /^(prof|profa|dr|dra|sr|sra)\.?$/i;

function initialsOf(name) {
    const letters = name.split(/\s+/).filter(word => word && !NAME_TITLES.test(word)).map(word => word[0].toUpperCase());
    return (letters.length > 1 ? letters[0] + letters[letters.length - 1] : letters[0] || '?');
}

/** Sem foto (ou foto que não carrega) aparecem as iniciais: foto ausente não atrapalha nada (RN-02). */
function personAvatar(speaker) {
    const photo = speaker.photoUrl && /^https?:\/\//i.test(speaker.photoUrl)
        ? `<img class="person-photo" src="${escapeHtml(speaker.photoUrl)}" alt="" loading="lazy" referrerpolicy="no-referrer" onerror="this.remove()">` : '';
    return `<span class="person-avatar" aria-hidden="true">${escapeHtml(initialsOf(speaker.name))}${photo}</span>`;
}

function renderHomeSpeakers(data) {
    const box = $('home-speakers');
    if (!data.speakers.length) { box.innerHTML = '<div class="empty-filter-state"><h4>Os palestrantes aparecem aqui</h4></div>'; return; }
    box.innerHTML = data.speakers.map(s => `
        <button type="button" class="person" data-action="homeEvent" data-id="${toId(s.eventId)}">
            ${personAvatar(s)}
            <span class="person-body">
                <span class="person-name">${escapeHtml(s.name)}</span>
                <span class="person-role">${escapeHtml(s.role)}${s.bio ? ' &middot; ' + escapeHtml(s.bio) : ''}</span>
                <span class="person-in">${escapeHtml(s.activityTitle)}</span>
            </span>
        </button>`).join('');
}

function renderHomeNotices(data) {
    const now = new Date();
    const items = data.events.filter(e => e.upcoming && registrationOpenNow(e)).map(e => {
        const urgent = e.registrationDeadline && (parseDate(e.registrationDeadline) - now) / 36e5 <= 72;
        const tag = urgent ? '<span class="tag-pill amber">Últimos dias</span>' : '<span class="tag-pill green">Inscrições abertas</span>';
        return `<li>${tag}<span>Inscrições para <strong>${escapeHtml(e.title)}</strong> até ${escapeHtml(formatDateTime(e.registrationDeadline))}.</span></li>`;
    });
    items.push('<li><span class="tag-pill">Presença</span><span>O código do QR Code é projetado na atividade e vale por 15 minutos. Se a leitura falhar, o organizador lança a presença manualmente.</span></li>');
    items.push('<li><span class="tag-pill">Certificado</span><span>É emitido a quem cumpre o critério de presença definido pelo organizador do evento.</span></li>');
    $('home-notices').innerHTML = items.join('');
}

function renderHomeCta() {
    let html;
    if (!isLoggedIn()) {
        html = `<div><h2>Pronto para participar?</h2><p>Crie sua conta para se inscrever, montar sua agenda e acompanhar seus certificados.</p></div>
            <div class="home-cta-actions"><button type="button" class="btn-action btn-primary" data-action="openRegister">Criar conta</button>
            <button type="button" class="btn-action btn-secondary" onclick="openAuthModal('login')">Já tenho conta</button></div>`;
    } else if (isStaff()) {
        html = `<div><h2>Painel do organizador</h2><p>Crie eventos, cadastre atividades, projete o QR Code da presença e acompanhe relatórios.</p></div>
            <div class="home-cta-actions"><button type="button" class="btn-action btn-primary" data-action="goTab" data-tab="admin">Abrir o painel</button></div>`;
    } else {
        html = `<div><h2>Sua participação</h2><p>Veja as atividades que você escolheu e o andamento dos seus certificados.</p></div>
            <div class="home-cta-actions"><button type="button" class="btn-action btn-primary" data-action="goTab" data-tab="agenda">Minha agenda</button>
            <button type="button" class="btn-action btn-secondary" data-action="goTab" data-tab="cert">Certificados</button></div>`;
    }
    $('home-cta').innerHTML = html;
}

/** Abre a programação de um evento (e, se pedido, a janela de detalhes de uma atividade). */
async function openEventFromHome(eventId, activityId) {
    if (eventId !== currentEventId) {
        currentEventId = eventId;
        await loadData();
        updateUI();
    }
    showTab('prog');
    if (activityId) openActivityDetail(activityId);
}

// ---------------------------------------------------------------- abas
function showTab(tab) {
    if ((tab === 'agenda' || tab === 'cert') && !isLoggedIn()) { openAuthModal(); return; }
    if (tab === 'admin' && !(isLoggedIn() && isStaff())) {
        notify('O painel do organizador é restrito a organizadores e administradores.', 'warning');
        return;
    }
    if (tab === 'users' && !(isLoggedIn() && isAdmin())) {
        notify('A gestão de usuários é restrita a administradores.', 'warning');
        return;
    }
    currentTab = tab;
    TABS.forEach(name => {
        const panel = $(`tab-${name}`);
        const nav = $(`nav-${name}`);
        if (panel) panel.style.display = name === tab ? 'block' : 'none';
        if (nav) {
            nav.classList.toggle('active', name === tab);
            if (name === tab) nav.setAttribute('aria-current', 'page'); else nav.removeAttribute('aria-current');
        }
    });
    syncEventbar(); // a página inicial não depende de um evento escolhido
    if (tab === 'prog' && !eventData) { eventChooser.clearSearch(); eventChooser.load(true); }
    if (tab === 'home') loadHome();
    syncBannerTimer();
    if (tab === 'agenda') renderAgenda();
    if (tab === 'cert') loadCertificateTab();
    if (tab === 'admin') loadAdminPanel();
    if (tab === 'users') loadUsers();
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// ---------------------------------------------------------------- eventos de clique (sem dados da API em atributos onclick)
const ACTIONS = {
    addToAgenda: d => addToAgenda(toId(d.id)),
    removeFromAgenda: d => removeFromAgenda(toId(d.id)),
    registerEvent: () => registerEvent(),
    toggleEventPicker: () => toggleEventPicker(),
    eventScope: d => { const list = EVENT_LISTS[d.list] || eventPicker; list.setScope(d.scope === 'mine'); list.load(true); },
    moreEvents: d => (EVENT_LISTS[d.list] || eventPicker).load(false),
    pickEvent: d => pickEvent(toId(d.id)),
    homeEvent: d => openEventFromHome(toId(d.id)),
    homeActivity: d => openEventFromHome(toId(d.event), toId(d.id)),
    browseEvents: () => { showTab('prog'); if (eventData) toggleEventPicker(true); },
    bannerPrev: () => { showBannerSlide(homeState.slide - 1); syncBannerTimer(); },
    bannerNext: () => { showBannerSlide(homeState.slide + 1); syncBannerTimer(); },
    bannerGo: d => { showBannerSlide(toId(d.index)); syncBannerTimer(); },
    openRegister: () => openAuthModal('register'),
    goTab: d => showTab(d.tab),
    newEvent: () => openEventForm(false),
    editEvent: () => openEventForm(true),
    eventLifecycle: d => changeEventLifecycle(d.step),
    openActivity: d => openActivityDetail(toId(d.id)),
    openAttendance: () => handleAttendanceClick(),
    projectActivity: d => { $('projector-act-select').value = String(toId(d.id)); openTeacherProjectorModal(); },
    openFeedback: d => openFeedbackModal(toId(d.id)),
    sendCertificateEmail: d => sendCertificateEmail(toId(d.id)),
    speakerDeclaration: d => downloadFile(`/api/activities/${toId(d.activity)}/speakers/${toId(d.speaker)}/declaration`,
        `declaracao_${toId(d.activity)}_${toId(d.speaker)}.pdf`),
    togglePassword: (d, el) => togglePasswordVisibility(el),
    downloadCertificate: d => downloadFile(`/api/certificates/download/${toId(d.id)}`, `certificado_${toId(d.id)}.pdf`),
    downloadReport: d => downloadReport(d),
    demoLogin: d => {
        const account = demoAccounts[toId(d.index)];
        toggleAccountDropdown(false);
        if (account) login(account.email, account.password);
    },
    manualAttend: d => manualAttend(toId(d.user)),
    revokeAttend: d => revokeAttend(toId(d.user)),
    removeQuestion: (d, el) => { const block = el.closest('.sv-q'); if (block) block.remove(); },
    saveUserRole: d => saveUserRole(toId(d.id)),
    clearFilters: () => clearFilters()
};

function onDocumentClick(event) {
    if (!$('event-picker-panel').hidden && !$('event-picker').contains(event.target)) toggleEventPicker(false);
    const dropdown = $('account-dropdown');
    const trigger = $('user-pill-trigger');
    if (dropdown.classList.contains('open') && !dropdown.contains(event.target) && !trigger.contains(event.target)) {
        toggleAccountDropdown(false);
    }
    const el = event.target.closest('[data-action]');
    if (!el || el.disabled) return;
    const handler = ACTIONS[el.dataset.action];
    if (!handler) return;
    // Ação disparada de dentro da janela de detalhes: fecha a janela antes, para não empilhar modais.
    if (el.closest('#modal-activity')) closeModal('modal-activity');
    handler(el.dataset, el);
}

function onDocumentChange(event) {
    if (event.target.classList.contains('sv-type')) {
        const block = event.target.closest('.sv-q');
        block.querySelector('.sv-options-wrap').style.display = event.target.value === 'SINGLE_CHOICE' ? 'block' : 'none';
    }
}

// ---------------------------------------------------------------- inicialização
async function init() {
    document.addEventListener('click', onDocumentClick);
    document.addEventListener('change', onDocumentChange);
    document.addEventListener('keydown', onKeyDown);
    $('auth-password').addEventListener('keydown', e => { if (e.key === 'Enter') login(); });
    $('choose-search').addEventListener('keydown', e => {
        if (e.key === 'Enter' && eventChooser.state.items.length) { e.preventDefault(); pickEvent(eventChooser.state.items[0].id); }
    });
    $('event-search').addEventListener('keydown', e => {
        if (e.key === 'Enter' && eventPicker.state.items.length) { e.preventDefault(); pickEvent(eventPicker.state.items[0].id); }
    });

    const banner = $('home-banner');
    const holdBanner = hold => () => { homeState.hovering = hold; syncBannerTimer(); };
    banner.addEventListener('mouseenter', holdBanner(true));
    banner.addEventListener('mouseleave', holdBanner(false));
    banner.addEventListener('focusin', holdBanner(true));
    banner.addEventListener('focusout', holdBanner(false));

    await Promise.all([loadDemoAccounts(), restoreSession()]);
    await loadEventsList();
    await loadData();
    updateUI();
    showTab('home');
}

document.addEventListener('DOMContentLoaded', init);
