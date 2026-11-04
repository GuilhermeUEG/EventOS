package com.eventos.application.services;

import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.AttendanceReportDto.ActivityAttendanceSummaryDto;
import com.eventos.application.dtos.AttendanceReportDto.ParticipantAttendanceRowDto;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.application.ports.input.ReportUseCase;
import com.eventos.application.ports.output.ActivityRepository;
import com.eventos.application.ports.output.AttendanceRepository;
import com.eventos.application.ports.output.EventRepository;
import com.eventos.application.ports.output.PdfGeneratorPort;
import com.eventos.application.ports.output.RegistrationRepository;
import com.eventos.application.ports.output.UserRepository;
import com.eventos.domain.exceptions.EntityNotFoundException;
import com.eventos.domain.model.Activity;
import com.eventos.domain.model.AttendanceRecord;
import com.eventos.domain.model.AttendanceStatus;
import com.eventos.domain.model.Event;
import com.eventos.domain.model.Registration;
import com.eventos.domain.model.RegistrationStatus;
import com.eventos.domain.model.User;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Relatórios operacionais. As consultas de apoio (usuários e registros de frequência) são feitas
 * em lote, de modo que o tempo não cresce com o produto participantes x atividades.
 */
public class ReportServiceImpl implements ReportUseCase {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final ActivityRepository activityRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final PdfGeneratorPort pdfGeneratorPort;

    public ReportServiceImpl(EventRepository eventRepository,
                             RegistrationRepository registrationRepository,
                             ActivityRepository activityRepository,
                             AttendanceRepository attendanceRepository,
                             UserRepository userRepository,
                             PdfGeneratorPort pdfGeneratorPort) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.activityRepository = activityRepository;
        this.attendanceRepository = attendanceRepository;
        this.userRepository = userRepository;
        this.pdfGeneratorPort = pdfGeneratorPort;
    }

    @Override
    public EnrolledReportDto getEnrolledReport(Long eventId) {
        Event event = findEvent(eventId);
        List<Registration> regs = registrationRepository.findByEventId(eventId);
        Map<Long, User> users = usersOf(regs);
        List<EnrolledReportDto.EnrolledParticipantDto> participants = new ArrayList<>();
        int confirmed = 0;
        int cancelled = 0;
        for (Registration r : regs) {
            User u = users.get(r.getUserId());
            if (u == null) continue;
            if (r.getStatus() == RegistrationStatus.CANCELLED) cancelled++;
            else if (r.isConfirmed()) confirmed++;
            participants.add(new EnrolledReportDto.EnrolledParticipantDto(
                    u.getId(), u.getName(), u.getEmail().getValue(),
                    r.getStatus().getDescription(), r.getRegistrationDate(),
                    r.getSelectedActivityIds().size()));
        }
        return new EnrolledReportDto(event.getId(), event.getTitle(), confirmed, cancelled,
                event.getMaxCapacity(), participants);
    }

    @Override
    public AttendanceReportDto getAttendanceReport(Long eventId) {
        Event event = findEvent(eventId);
        List<Activity> activities = activityRepository.findByEventId(eventId);
        List<Registration> confirmed = registrationRepository.findByEventId(eventId).stream()
                .filter(Registration::isConfirmed).toList();
        Map<Long, User> users = usersOf(confirmed);
        Map<String, List<AttendanceRecord>> recordsByActivityAndUser = new HashMap<>();
        for (AttendanceRecord r : attendanceRepository.findByEventId(eventId)) {
            recordsByActivityAndUser.computeIfAbsent(r.getActivityId() + ":" + r.getUserId(), k -> new ArrayList<>()).add(r);
        }

        List<ActivityAttendanceSummaryDto> summaries = new ArrayList<>();
        List<ParticipantAttendanceRowDto> rows = new ArrayList<>();
        int totalPresent = 0;
        int totalSlots = 0;
        for (Activity act : activities) {
            int present = 0;
            int partial = 0;
            int absent = 0;
            for (Registration reg : confirmed) {
                User user = users.get(reg.getUserId());
                if (user == null || !event.admits(reg, act)) continue;
                List<AttendanceRecord> records = recordsByActivityAndUser.getOrDefault(act.getId() + ":" + user.getId(), List.of());
                AttendanceStatus status = act.evaluateAttendance(records);
                if (status == AttendanceStatus.PRESENT) present++;
                else if (status == AttendanceStatus.PARTIAL) partial++;
                else absent++;
                AttendanceRecord last = records.stream()
                        .max(Comparator.comparing(AttendanceRecord::getTimestamp)
                                .thenComparing(r -> r.getId() == null ? Long.MAX_VALUE : r.getId()))
                        .orElse(null);
                rows.add(new ParticipantAttendanceRowDto(user.getId(), user.getName(), user.getEmail().getValue(),
                        act.getId(), act.getTitle(), status, records.size(),
                        last == null ? null : last.getTimestamp(), last == null ? "-" : last.getRecordedBy()));
            }
            summaries.add(new ActivityAttendanceSummaryDto(act.getId(), act.getTitle(), act.getLocation().getRoom(),
                    present, partial, absent));
            totalPresent += present;
            totalSlots += present + partial + absent;
        }
        double rate = totalSlots > 0 ? ((double) totalPresent / totalSlots) * 100.0 : 0.0;
        return new AttendanceReportDto(event.getId(), event.getTitle(), confirmed.size(), rate, summaries, rows);
    }

    @Override
    public byte[] exportEnrolledCsv(Long eventId) {
        EnrolledReportDto report = getEnrolledReport(eventId);
        StringBuilder sb = new StringBuilder("ID_PARTICIPANTE,NOME,EMAIL,STATUS_INSCRICAO,DATA_INSCRICAO,QTD_ATIVIDADES_ESCOLHIDAS\n");
        for (EnrolledReportDto.EnrolledParticipantDto p : report.getParticipants()) {
            sb.append(p.getUserId()).append(',')
                    .append(csv(p.getName())).append(',')
                    .append(csv(p.getEmail())).append(',')
                    .append(csv(p.getStatus())).append(',')
                    .append(csv(p.getRegistrationDate().format(DATE_TIME))).append(',')
                    .append(p.getSelectedActivitiesCount()).append('\n');
        }
        return withBom(sb);
    }

    @Override
    public byte[] exportAttendanceCsv(Long eventId) {
        AttendanceReportDto report = getAttendanceReport(eventId);
        StringBuilder sb = new StringBuilder(
                "ID_PARTICIPANTE,NOME,EMAIL,ID_ATIVIDADE,ATIVIDADE,SITUACAO,QTD_REGISTROS,ULTIMO_REGISTRO,ORIGEM\n");
        for (ParticipantAttendanceRowDto row : report.getRows()) {
            sb.append(row.getUserId()).append(',')
                    .append(csv(row.getUserName())).append(',')
                    .append(csv(row.getUserEmail())).append(',')
                    .append(row.getActivityId()).append(',')
                    .append(csv(row.getActivityTitle())).append(',')
                    .append(csv(row.getStatus().getDescription())).append(',')
                    .append(row.getRecordsCount()).append(',')
                    .append(csv(row.getLastRecordTime() == null ? "" : row.getLastRecordTime().format(DATE_TIME))).append(',')
                    .append(csv(row.getLastRecordedBy())).append('\n');
        }
        return withBom(sb);
    }

    @Override
    public byte[] exportEnrolledPdf(Long eventId) {
        return pdfGeneratorPort.generateEnrolledReportPdf(getEnrolledReport(eventId));
    }

    @Override
    public byte[] exportAttendancePdf(Long eventId) {
        return pdfGeneratorPort.generateAttendanceReportPdf(getAttendanceReport(eventId));
    }

    private Map<Long, User> usersOf(List<Registration> regs) {
        Map<Long, User> users = new HashMap<>();
        userRepository.findByIds(regs.stream().map(Registration::getUserId).distinct().toList())
                .forEach(u -> users.put(u.getId(), u));
        return users;
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento não encontrado."));
    }

    /** Escapa aspas e neutraliza fórmulas (=, +, -, @) que planilhas executariam ao abrir o arquivo. */
    static String csv(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    /** UTF-8 com BOM para que o Excel abra acentos corretamente. */
    private byte[] withBom(StringBuilder sb) {
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[body.length + 3];
        out[0] = (byte) 0xEF;
        out[1] = (byte) 0xBB;
        out[2] = (byte) 0xBF;
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }
}
