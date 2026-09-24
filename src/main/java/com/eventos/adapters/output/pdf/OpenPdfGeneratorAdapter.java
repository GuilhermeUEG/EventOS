package com.eventos.adapters.output.pdf;

import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.application.ports.output.PdfGeneratorPort;
import com.eventos.domain.model.Certificate;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

public class OpenPdfGeneratorAdapter implements PdfGeneratorPort {

    @Override
    public byte[] generateCertificatePdf(Certificate certificate) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 28, new Color(30, 58, 138));
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(75, 85, 99));
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 14, Color.BLACK);
            Font highlightFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(37, 99, 235));
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, Color.GRAY);

            Paragraph header = new Paragraph("CERTIFICADO DE PARTICIPACAO", titleFont);
            header.setAlignment(Element.ALIGN_CENTER);
            header.setSpacingAfter(20);
            document.add(header);

            Paragraph subHeader = new Paragraph("EventOS - Plataforma de Gestao de Eventos", subTitleFont);
            subHeader.setAlignment(Element.ALIGN_CENTER);
            subHeader.setSpacingAfter(30);
            document.add(subHeader);

            Paragraph body = new Paragraph();
            body.setAlignment(Element.ALIGN_CENTER);
            body.setLeading(24);
            body.add(new Chunk("Certificamos para os devidos fins que\n", bodyFont));
            body.add(new Chunk(certificate.getParticipantName().toUpperCase() + "\n", highlightFont));
            body.add(new Chunk("participou com exito do evento ", bodyFont));
            body.add(new Chunk("\"" + certificate.getEventTitle() + "\"", highlightFont));
            body.add(new Chunk(", totalizando uma carga horaria comprovada de ", bodyFont));
            body.add(new Chunk(String.format("%.1f horas", certificate.getCompletionHours()) + ".\n\n", highlightFont));
            document.add(body);

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            Paragraph dateP = new Paragraph("Emitido em " + certificate.getIssueDate().format(fmt), bodyFont);
            dateP.setAlignment(Element.ALIGN_CENTER);
            dateP.setSpacingAfter(30);
            document.add(dateP);

            Paragraph footer = new Paragraph();
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.add(new Chunk("Codigo de Autenticidade Verificavel: " + certificate.getVerificationCode() + "\n", highlightFont));
            footer.add(new Chunk("Valide este documento em: http://localhost:7000/api/certificates/verify/" + certificate.getVerificationCode(), smallFont));
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF do certificado", e);
        }
    }

    @Override
    public byte[] generateEnrolledReportPdf(EnrolledReportDto report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 58, 138));
            Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

            Paragraph title = new Paragraph("Relatorio Operacional de Inscritos", titleFont);
            title.setSpacingAfter(10);
            document.add(title);

            Paragraph meta = new Paragraph("Evento: " + report.getEventTitle() + " | Total de Inscritos: " +
                    report.getTotalEnrolled() + " (Capacidade Maxima: " + report.getEventCapacity() + ")\n\n", textFont);
            document.add(meta);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.0f, 3.5f, 3.5f, 2.0f, 2.0f});

            String[] headers = {"ID", "Nome", "E-mail", "Situacao", "Atividades"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headFont));
                cell.setBackgroundColor(new Color(37, 99, 235));
                cell.setPadding(6);
                table.addCell(cell);
            }

            for (EnrolledReportDto.EnrolledParticipantDto p : report.getParticipants()) {
                table.addCell(new Phrase(String.valueOf(p.getUserId()), textFont));
                table.addCell(new Phrase(p.getName(), textFont));
                table.addCell(new Phrase(p.getEmail(), textFont));
                table.addCell(new Phrase(p.getStatus(), textFont));
                table.addCell(new Phrase(p.getSelectedActivitiesCount() + " selecionada(s)", textFont));
            }

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatorio de inscritos em PDF", e);
        }
    }

    @Override
    public byte[] generateAttendanceReportPdf(AttendanceReportDto report) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(30, 58, 138));
            Font textFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

            Paragraph title = new Paragraph("Relatorio de Frequencia e Participacao", titleFont);
            title.setSpacingAfter(10);
            document.add(title);

            Paragraph meta = new Paragraph("Evento: " + report.getEventTitle() +
                    " | Taxa Geral de Presenca: " + String.format("%.1f%%", report.getOverallAttendanceRate()) + "\n\n", textFont);
            document.add(meta);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.0f, 4.0f, 2.5f, 2.0f, 2.0f});

            String[] headers = {"ID", "Atividade", "Sala", "Presentes", "Ausentes"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headFont));
                cell.setBackgroundColor(new Color(15, 118, 110));
                cell.setPadding(6);
                table.addCell(cell);
            }

            for (AttendanceReportDto.ActivityAttendanceSummaryDto s : report.getActivitySummaries()) {
                table.addCell(new Phrase(String.valueOf(s.getActivityId()), textFont));
                table.addCell(new Phrase(s.getActivityTitle(), textFont));
                table.addCell(new Phrase(s.getRoom(), textFont));
                table.addCell(new Phrase(String.valueOf(s.getTotalPresent()), textFont));
                table.addCell(new Phrase(String.valueOf(s.getTotalAbsent()), textFont));
            }

            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatorio de presenca em PDF", e);
        }
    }
}
