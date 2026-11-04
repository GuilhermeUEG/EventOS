package com.eventos.adapters.output.pdf;

import com.eventos.application.dtos.AttendanceReportDto;
import com.eventos.application.dtos.EnrolledReportDto;
import com.eventos.application.dtos.SpeakerDeclarationDto;
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

/**
 * Adaptador de saída que gera os PDFs de certificado e de relatórios com OpenPDF.
 */
public class OpenPdfGeneratorAdapter implements PdfGeneratorPort {
    private static final int MAX_DETAIL_ROWS = 1000;
    private static final String LOGO_RESOURCE = "/public/logo-mark.png";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String verificationBaseUrl;

    public OpenPdfGeneratorAdapter() {
        this("http://localhost:7000");
    }

    public OpenPdfGeneratorAdapter(String verificationBaseUrl) {
        this.verificationBaseUrl = verificationBaseUrl;
    }

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

            addLogo(document);
            Paragraph header = new Paragraph("CERTIFICADO DE PARTICIPAÇÃO", titleFont);
            header.setAlignment(Element.ALIGN_CENTER);
            header.setSpacingAfter(20);
            document.add(header);

            Paragraph subHeader = new Paragraph("EventOS - Plataforma de Gestão de Eventos", subTitleFont);
            subHeader.setAlignment(Element.ALIGN_CENTER);
            subHeader.setSpacingAfter(30);
            document.add(subHeader);

            Paragraph body = new Paragraph();
            body.setAlignment(Element.ALIGN_CENTER);
            body.setLeading(24);
            body.add(new Chunk("Certificamos para os devidos fins que\n", bodyFont));
            body.add(new Chunk(certificate.getParticipantName().toUpperCase() + "\n", highlightFont));
            body.add(new Chunk("participou com êxito do evento ", bodyFont));
            body.add(new Chunk("\"" + certificate.getEventTitle() + "\"", highlightFont));
            body.add(new Chunk(", totalizando uma carga horária comprovada de ", bodyFont));
            body.add(new Chunk(String.format("%.1f horas", certificate.getCompletionHours()) + ".\n\n", highlightFont));
            document.add(body);

            Paragraph dateP = new Paragraph("Emitido em "
                    + certificate.getIssueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), bodyFont);
            dateP.setAlignment(Element.ALIGN_CENTER);
            dateP.setSpacingAfter(30);
            document.add(dateP);

            Paragraph footer = new Paragraph();
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.add(new Chunk("Código de Autenticidade Verificável: " + certificate.getVerificationCode() + "\n", highlightFont));
            footer.add(new Chunk("Valide este documento em: " + verificationBaseUrl
                    + "/api/certificates/verify/" + certificate.getVerificationCode(), smallFont));
            document.add(footer);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF do certificado", e);
        }
    }

    @Override
    public byte[] generateSpeakerDeclarationPdf(SpeakerDeclarationDto d) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 26, new Color(30, 58, 138));
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 14, Color.BLACK);
            Font highlightFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(37, 99, 235));

            addLogo(document);
            Paragraph header = new Paragraph("DECLARAÇÃO", titleFont);
            header.setAlignment(Element.ALIGN_CENTER);
            header.setSpacingAfter(30);
            document.add(header);

            Paragraph body = new Paragraph();
            body.setAlignment(Element.ALIGN_CENTER);
            body.setLeading(24);
            body.add(new Chunk("Declaramos que\n", bodyFont));
            body.add(new Chunk(d.speakerName().toUpperCase() + "\n", highlightFont));
            body.add(new Chunk("atuou como ", bodyFont));
            body.add(new Chunk(d.roleInActivity(), highlightFont));
            body.add(new Chunk(" na atividade ", bodyFont));
            body.add(new Chunk("\"" + d.activityTitle() + "\"", highlightFont));
            body.add(new Chunk(", integrante do evento ", bodyFont));
            body.add(new Chunk("\"" + d.eventTitle() + "\"", highlightFont));
            body.add(new Chunk(", realizada em " + d.activityStart().format(DATE_TIME) + " (duração de "
                    + String.format("%.1f", d.activityHours()) + " hora(s)).\n\n", bodyFont));
            document.add(body);

            Paragraph issued = new Paragraph("Emitida em " + d.issueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), bodyFont);
            issued.setAlignment(Element.ALIGN_CENTER);
            document.add(issued);

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar declaração em PDF", e);
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

            Paragraph title = new Paragraph("Relatório Operacional de Inscritos", titleFont);
            title.setSpacingAfter(10);
            document.add(title);

            document.add(new Paragraph("Evento: " + report.getEventTitle()
                    + " | Inscrições confirmadas: " + report.getTotalEnrolled()
                    + " | Canceladas: " + report.getTotalCancelled()
                    + " | Capacidade máxima: " + report.getEventCapacity() + "\n\n", textFont));

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.0f, 3.5f, 3.5f, 2.0f, 2.0f});
            addHeaders(table, headFont, new Color(37, 99, 235), "ID", "Nome", "E-mail", "Situação", "Atividades");

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
            throw new RuntimeException("Erro ao gerar relatório de inscritos em PDF", e);
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
            Color headColor = new Color(15, 118, 110);

            Paragraph title = new Paragraph("Relatório de Frequência e Participação", titleFont);
            title.setSpacingAfter(10);
            document.add(title);

            document.add(new Paragraph("Evento: " + report.getEventTitle()
                    + " | Participantes: " + report.getTotalParticipants()
                    + " | Taxa geral de presença: " + String.format("%.1f%%", report.getOverallAttendanceRate())
                    + "\n\n", textFont));

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.0f, 4.0f, 2.5f, 1.8f, 1.8f, 1.8f});
            addHeaders(table, headFont, headColor, "ID", "Atividade", "Sala", "Presentes", "Parciais", "Ausentes");
            for (AttendanceReportDto.ActivityAttendanceSummaryDto s : report.getActivitySummaries()) {
                table.addCell(new Phrase(String.valueOf(s.getActivityId()), textFont));
                table.addCell(new Phrase(s.getActivityTitle(), textFont));
                table.addCell(new Phrase(s.getRoom(), textFont));
                table.addCell(new Phrase(String.valueOf(s.getTotalPresent()), textFont));
                table.addCell(new Phrase(String.valueOf(s.getTotalPartial()), textFont));
                table.addCell(new Phrase(String.valueOf(s.getTotalAbsent()), textFont));
            }
            document.add(table);

            Paragraph detailTitle = new Paragraph("\nSituação por participante e atividade", titleFont);
            detailTitle.setSpacingAfter(6);
            document.add(detailTitle);

            PdfPTable detail = new PdfPTable(5);
            detail.setWidthPercentage(100);
            detail.setWidths(new float[]{3.0f, 4.0f, 2.0f, 1.2f, 3.0f});
            addHeaders(detail, headFont, headColor, "Participante", "Atividade", "Situação", "Regs.", "Última marcação");
            int printed = 0;
            for (AttendanceReportDto.ParticipantAttendanceRowDto row : report.getRows()) {
                if (printed++ >= MAX_DETAIL_ROWS) break;
                detail.addCell(new Phrase(row.getUserName(), textFont));
                detail.addCell(new Phrase(row.getActivityTitle(), textFont));
                detail.addCell(new Phrase(row.getStatus().getDescription(), textFont));
                detail.addCell(new Phrase(String.valueOf(row.getRecordsCount()), textFont));
                detail.addCell(new Phrase(row.getLastRecordTime() == null ? "-"
                        : row.getLastRecordTime().format(DATE_TIME) + " (" + row.getLastRecordedBy() + ")", textFont));
            }
            document.add(detail);
            if (report.getRows().size() > MAX_DETAIL_ROWS) {
                document.add(new Paragraph("Exibindo as primeiras " + MAX_DETAIL_ROWS + " linhas de "
                        + report.getRows().size() + ". Use a exportação CSV para o relatório completo.", textFont));
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar relatório de presença em PDF", e);
        }
    }

    /** Símbolo do EventOS centralizado no topo do documento; se o arquivo faltar, o documento segue sem ele. */
    private void addLogo(Document document) {
        try (java.io.InputStream in = OpenPdfGeneratorAdapter.class.getResourceAsStream(LOGO_RESOURCE)) {
            if (in == null) return;
            com.lowagie.text.Image logo = com.lowagie.text.Image.getInstance(in.readAllBytes());
            logo.scaleToFit(40, 40);
            logo.setAlignment(Element.ALIGN_CENTER);
            logo.setSpacingAfter(8);
            document.add(logo);
        } catch (Exception e) {
            // marca é opcional; nunca impede a emissão do certificado
        }
    }

    private void addHeaders(PdfPTable table, Font font, Color background, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, font));
            cell.setBackgroundColor(background);
            cell.setPadding(6);
            table.addCell(cell);
        }
    }
}
