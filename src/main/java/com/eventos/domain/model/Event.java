package com.eventos.domain.model;

import com.eventos.domain.exceptions.BusinessRuleException;
import com.eventos.domain.exceptions.ValidationException;
import com.eventos.domain.policies.CertificateEligibilityPolicy;
import com.eventos.domain.policies.CertificatePolicyFactory;
import com.eventos.domain.services.ConflictValidator;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Agregado raiz do evento: estado, programação e regras de inscrição (ROO-01, ROO-02, RF-04, RF-12 a RF-15, RN-04, RN-05).
 *
 * <p>As datas do evento e de suas atividades são horários locais do fuso do próprio evento (RN-19);
 * o "agora" usado nas regras de prazo vem de {@link #now()}, sempre no fuso do evento.</p>
 */
public class Event implements Serializable {
    public static final String DEFAULT_TIME_ZONE = "America/Sao_Paulo";

    private final Long id;
    private String title;
    private String description;
    private Period period;
    private EventStatus status;
    private final Long organizerId;
    private final List<Activity> activities;
    private int maxCapacity;
    private CertificateEligibilityPolicy certificateEligibilityPolicy;
    private boolean activitySelectionEnabled;
    private boolean activitySelectionRequired;
    private LocalDateTime registrationDeadline;
    private String timeZone;

    public Event(Long id, String title, String description, Period period,
                 EventStatus status, Long organizerId, List<Activity> activities,
                 int maxCapacity, CertificateEligibilityPolicy certificateEligibilityPolicy) {
        if (title == null || title.trim().isEmpty()) {
            throw new ValidationException("Título do evento é obrigatório.");
        }
        if (period == null) {
            throw new ValidationException("Período do evento é obrigatório.");
        }
        this.id = id;
        this.title = title.trim();
        this.description = description != null ? description.trim() : "";
        this.period = period;
        this.status = status != null ? status : EventStatus.DRAFT;
        this.organizerId = organizerId;
        this.activities = activities != null ? new ArrayList<>(activities) : new ArrayList<>();
        this.maxCapacity = maxCapacity > 0 ? maxCapacity : 500;
        this.certificateEligibilityPolicy = certificateEligibilityPolicy != null
                ? certificateEligibilityPolicy
                : CertificatePolicyFactory.defaultPolicy();
        this.activitySelectionEnabled = true;
        this.activitySelectionRequired = false;
        this.registrationDeadline = period.getStart();
        this.timeZone = DEFAULT_TIME_ZONE;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Period getPeriod() {
        return period;
    }

    public EventStatus getStatus() {
        return status;
    }

    public Long getOrganizerId() {
        return organizerId;
    }

    public List<Activity> getActivities() {
        return Collections.unmodifiableList(activities);
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public CertificateEligibilityPolicy getCertificateEligibilityPolicy() {
        return certificateEligibilityPolicy;
    }

    public boolean isActivitySelectionEnabled() {
        return activitySelectionEnabled;
    }

    public boolean isActivitySelectionRequired() {
        return activitySelectionRequired;
    }

    public LocalDateTime getRegistrationDeadline() {
        return registrationDeadline;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public ZoneId zoneId() {
        return ZoneId.of(timeZone);
    }

    /** Horário atual no fuso do evento (RN-19). */
    public LocalDateTime now() {
        return LocalDateTime.now(zoneId());
    }

    // ---------------------------------------------------------------- configuração

    /**
     * Troca a política de certificado. Depois do encerramento ela fica congelada, para que
     * certificados futuros não sigam regra diferente da vigente durante o evento (RN-16).
     */
    public void changeCertificatePolicy(CertificateEligibilityPolicy policy) {
        if (policy == null) {
            throw new ValidationException("Política de certificado é obrigatória.");
        }
        if (status == EventStatus.FINISHED || status == EventStatus.CANCELLED) {
            throw new BusinessRuleException("A política de certificado não pode mudar em evento encerrado ou cancelado.");
        }
        this.certificateEligibilityPolicy = policy;
    }

    public void configureRegistration(boolean selectionEnabled, boolean selectionRequired,
                                      LocalDateTime deadline, String timeZone) {
        if (selectionRequired && !selectionEnabled) {
            throw new ValidationException("A seleção não pode ser obrigatória quando a escolha de atividades está desativada.");
        }
        if (deadline != null && deadline.isAfter(period.getEnd())) {
            throw new ValidationException("O prazo de inscrição não pode ser posterior ao encerramento do evento.");
        }
        if (timeZone == null || timeZone.isBlank()) {
            throw new ValidationException("Fuso horário do evento é obrigatório.");
        }
        try {
            ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new ValidationException("Fuso horário inválido: " + timeZone);
        }
        this.activitySelectionEnabled = selectionEnabled;
        this.activitySelectionRequired = selectionRequired;
        this.registrationDeadline = deadline != null ? deadline : period.getStart();
        this.timeZone = timeZone;
    }

    public void updateDetails(String newTitle, String newDescription, Period newPeriod, int newCapacity) {
        if (this.status == EventStatus.FINISHED || this.status == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Não é permitido editar evento encerrado ou cancelado.");
        }
        if (newPeriod != null) {
            for (Activity activity : activities) {
                if (!activity.getPeriod().isWithin(newPeriod)) {
                    throw new BusinessRuleException("O novo período não comporta a atividade '" + activity.getTitle()
                            + "' (" + activity.getPeriod() + ").");
                }
            }
            if (registrationDeadline != null && registrationDeadline.isAfter(newPeriod.getEnd())) {
                throw new BusinessRuleException("O prazo de inscrição atual é posterior ao novo encerramento do evento.");
            }
            this.period = newPeriod;
        }
        if (newTitle != null && !newTitle.trim().isEmpty()) this.title = newTitle.trim();
        if (newDescription != null) this.description = newDescription.trim();
        if (newCapacity > 0) this.maxCapacity = newCapacity;
    }

    // ---------------------------------------------------------------- ciclo de vida

    public void publish() {
        if (this.status == EventStatus.CANCELLED || this.status == EventStatus.FINISHED) {
            throw new BusinessRuleException("Um evento cancelado ou encerrado não pode ser publicado.");
        }
        if (this.activities.isEmpty()) {
            throw new BusinessRuleException("Para publicar um evento, cadastre ao menos uma atividade na programação.");
        }
        this.status = EventStatus.PUBLISHED;
    }

    public void cancel() {
        if (this.status == EventStatus.FINISHED) {
            throw new BusinessRuleException("Evento já encerrado não pode ser cancelado.");
        }
        this.status = EventStatus.CANCELLED;
    }

    public void finish() {
        if (this.status != EventStatus.PUBLISHED && this.status != EventStatus.IN_PROGRESS) {
            throw new BusinessRuleException("Somente eventos publicados ou em andamento podem ser encerrados.");
        }
        this.status = EventStatus.FINISHED;
    }

    /** O estado controla a visibilidade pública (RN-04). */
    public boolean isPubliclyVisible() {
        return this.status == EventStatus.PUBLISHED || this.status == EventStatus.IN_PROGRESS;
    }

    public boolean isEnrollmentOpen() {
        return isPubliclyVisible();
    }

    public boolean isRegistrationWithinDeadline(LocalDateTime now) {
        return registrationDeadline == null || !now.isAfter(registrationDeadline);
    }

    // ---------------------------------------------------------------- regras de inscrição

    /**
     * Verifica se uma nova inscrição pode ser aceita agora (RN-04, RN-05, RF-12 a RF-14).
     *
     * @param confirmedRegistrations inscrições confirmadas atualmente no evento
     * @param selectedActivityIds    atividades escolhidas pelo participante (pode ser vazio)
     */
    public void assertCanRegister(LocalDateTime now, int confirmedRegistrations, Set<Long> selectedActivityIds) {
        if (!isEnrollmentOpen()) {
            throw new BusinessRuleException("As inscrições para este evento estão fechadas (" + status.getDescription() + ").");
        }
        if (!isRegistrationWithinDeadline(now)) {
            throw new BusinessRuleException("O prazo de inscrição deste evento foi encerrado.");
        }
        boolean hasSelection = selectedActivityIds != null && !selectedActivityIds.isEmpty();
        if (activitySelectionRequired && !hasSelection) {
            throw new BusinessRuleException("Selecione ao menos uma atividade para concluir a inscrição.");
        }
        if (!activitySelectionEnabled && hasSelection) {
            throw new BusinessRuleException("Este evento não permite escolha individual de atividades.");
        }
        if (confirmedRegistrations >= maxCapacity) {
            throw new BusinessRuleException("O evento atingiu a capacidade máxima de " + maxCapacity + " participantes.");
        }
    }

    /**
     * A inscrição dá acesso à atividade? Quando o evento não permite escolher atividades (RN-05), a inscrição
     * confirmada no evento basta para todas elas; caso contrário vale a escolha do participante.
     */
    public boolean admits(Registration registration, Activity activity) {
        if (registration == null || activity == null || !registration.isConfirmed()) return false;
        if (!Objects.equals(id, registration.getEventId()) || !Objects.equals(id, activity.getEventId())) return false;
        return !activitySelectionEnabled || registration.grantsAccessTo(activity);
    }

    public void assertActivitySelectionEnabled() {
        if (!activitySelectionEnabled) {
            throw new BusinessRuleException("Este evento não permite escolha individual de atividades.");
        }
    }

    /** Escolher ou remover atividades segue as mesmas regras de abertura e prazo da inscrição. */
    public void assertCanChangeSelection(LocalDateTime now) {
        if (!isEnrollmentOpen()) {
            throw new BusinessRuleException("As inscrições para este evento estão fechadas (" + status.getDescription() + ").");
        }
        if (!isRegistrationWithinDeadline(now)) {
            throw new BusinessRuleException("O prazo de inscrição deste evento foi encerrado.");
        }
    }

    public void assertCanCancelRegistration(LocalDateTime now) {
        if (!isRegistrationWithinDeadline(now)) {
            throw new BusinessRuleException("O prazo para cancelamento da inscrição foi encerrado.");
        }
    }

    // ---------------------------------------------------------------- programação

    public void addActivity(Activity activity) {
        if (activity == null) throw new ValidationException("Atividade não pode ser nula.");
        if (this.status == EventStatus.FINISHED || this.status == EventStatus.CANCELLED) {
            throw new BusinessRuleException("Não é possível alterar a programação de evento encerrado ou cancelado.");
        }
        if (this.id != null && activity.getEventId() != null && !activity.getEventId().equals(this.id)) {
            throw new BusinessRuleException("A atividade pertence a outro evento.");
        }
        // Invariante: a atividade deve estar contida no período do evento
        if (!activity.getPeriod().isWithin(this.period)) {
            throw new BusinessRuleException("O horário da atividade (" + activity.getPeriod() + ") deve estar dentro do período do evento (" + this.period + ").");
        }
        // Invariante: nenhuma sala pode ter duas atividades ao mesmo tempo (RF-07, RN-07)
        ConflictValidator.validateActivityRoomConflict(activity, this.activities);

        if (this.id != null) {
            activity.assignToEvent(this.id);
        }
        this.activities.add(activity);
    }

    public double calculateTotalHours() {
        return this.activities.stream()
                .mapToDouble(a -> a.getPeriod().getDurationHours())
                .sum();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return id != null && Objects.equals(id, event.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
