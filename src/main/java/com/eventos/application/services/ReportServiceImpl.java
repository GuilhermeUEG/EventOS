package com.eventos.application.services;

import com.eventos.application.dtos.AttendanceReportDto;
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
import com.eventos.domain.model.User;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReportServiceImpl implements ReportUseCase {
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
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento nao encontrado."));
        List<Registration> regs = registrationRepository.findByEventId(eventId);
        
        List<EnrolledReportDto.EnrolledParticipantDto> participantDtos = new ArrayList<>();
        for (Registration r : regs) {
            User u = userRepository.findById(r.getUserId()).orElse(null);
            if (u != null) {
                participantDtos.add(new EnrolledReportDto.EnrolledParticipantDto(
                        u.getId(), u.getName(), u.getEmail().getValue(),
                        r.getStatus().getDescription(), r.getRegistrationDate(),
                        r.getSelectedActivityIds().size()
                ));
            }
        }

        return new EnrolledReportDto(event.getId(), event.getTitle(), regs.size(), event.getMaxCapacity(), participantDtos);
    }

    @Override
    public AttendanceReportDto getAttendanceReport(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Evento nao encontrado."));
        List<Activity> activities = activityRepository.findByEventId(eventId);
        List<Registration> regs = registrationRepository.findByEventId(eventId);

        List<AttendanceReportDto.ActivityAttendanceSummaryDto> summaries = new ArrayList<>();
        int totalPresentCount = 0;
        int totalSlotsCount = 0;

        for (Activity act : activities) {
            int present = 0;
            int partial = 0;
            int absent = 0;

            for (Registration reg : regs) {
                if (!reg.isConfirmed()) continue;
                List<AttendanceRecord> records = attendanceRepository.findByActivityAndUser(act.getId(), reg.getUserId());
                AttendanceStatus status = act.evaluateAttendance(records);
                if (status == AttendanceStatus.PRESENT) present++;
                else if (status == AttendanceStatus.PARTIAL) partial++;
                else absent++;
            }

            summaries.add(new AttendanceReportDto.ActivityAttendanceSummaryDto(
                    act.getId(), act.getTitle(), act.getLocation().getRoom(), present, partial, absent
            ));
            totalPresentCount += present;
            totalSlotsCount += (present + partial + absent);
        }

        double rate = totalSlotsCount > 0 ? ((double) totalPresentCount / totalSlotsCount) * 100.0 : 0.0;
        return new AttendanceReportDto(event.getId(), event.getTitle(), regs.size(), rate, summaries);
    }

    @Override
    public byte[] exportEnrolledCsv(Long eventId) {
        EnrolledReportDto report = getEnrolledReport(eventId);
        StringBuilder sb = new StringBuilder();
        sb.append("ID_PARTICIPANTE,NOME,EMAIL,STATUS_INSCRICAO,DATA_INSCRICAO,QTD_ATIVIDADES_ESCOLHIDAS\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        for (EnrolledReportDto.EnrolledParticipantDto p : report.getParticipants()) {
            sb.append(p.getUserId()).append(",")
              .append("\"").append(p.getName().replace("\"", "\"\"")).append("\",")
              .append("\"").append(p.getEmail()).append("\",")
              .append("\"").append(p.getStatus()).append("\",")
              .append("\"").append(p.getRegistrationDate().format(fmt)).append("\",")
              .append(p.getSelectedActivitiesCount()).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] exportEnrolledPdf(Long eventId) {
        EnrolledReportDto report = getEnrolledReport(eventId);
        return pdfGeneratorPort.generateEnrolledReportPdf(report);
    }

    @Override
    public byte[] exportAttendancePdf(Long eventId) {
        AttendanceReportDto report = getAttendanceReport(eventId);
        return pdfGeneratorPort.generateAttendanceReportPdf(report);
    }
}
