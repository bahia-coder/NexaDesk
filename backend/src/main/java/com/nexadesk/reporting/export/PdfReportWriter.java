package com.nexadesk.reporting.export;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.nexadesk.reporting.dto.AnalyticsDtos.CountItem;
import com.nexadesk.reporting.dto.AnalyticsDtos.TechnicianPerformance;
import com.nexadesk.reporting.dto.AnalyticsDtos.TimelinePoint;
import com.nexadesk.reporting.dto.ReportDtos.Report;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Report as a printable A4 PDF. Uses the standard PDF Helvetica font (embedded in every PDF reader, covers French
 * accents): no font file needs to exist on the server.
 */
@Component
public class PdfReportWriter {

    private static final Color INDIGO = new Color(0x4F, 0x46, 0xE5);
    private static final Color STRIPE = new Color(0xF1, 0xF5, 0xF9);
    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, INDIGO);
    private static final Font SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(0x0F, 0x17, 0x2A));
    private static final Font MUTED = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(0x64, 0x74, 0x8B));
    private static final Font HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);

    private final ZoneId zone;

    public PdfReportWriter(@Value("${nexadesk.time-zone}") ZoneId zone) {
        this.zone = zone;
    }

    public byte[] write(Report r) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);
        PdfWriter.getInstance(document, out);
        document.open();

        document.add(new Paragraph("NexaDesk — Rapport d'activité", TITLE));
        document.add(new Paragraph(ReportLabels.scope(r.scope()), MUTED));
        document.add(new Paragraph("Généré le " + DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE)
                .format(r.generatedAt().atZone(zone)), MUTED));

        PdfPTable summary = table(new float[] {3, 1}, "Indicateur", "Valeur");
        row(summary, "Tickets créés", String.valueOf(r.summary().created()));
        row(summary, "Tickets résolus", String.valueOf(r.summary().resolved()));
        row(summary, "Taux de résolution", ReportLabels.number(r.summary().resolutionRate(), " %"));
        row(summary, "Temps moyen de résolution", ReportLabels.number(r.summary().avgResolutionHours(), " h"));
        row(summary, "Respect SLA — première réponse", ReportLabels.number(r.summary().sla().firstResponseRate(), " %"));
        row(summary, "Respect SLA — résolution", ReportLabels.number(r.summary().sla().resolutionRate(), " %"));
        row(summary, "Satisfaction moyenne", ReportLabels.number(r.satisfaction().average(), " / 5")
                + " (" + r.satisfaction().count() + " avis)");
        section(document, "Synthèse", summary);

        section(document, "Tickets créés par catégorie", counts("Catégorie", r.byCategory()));
        section(document, "Tickets créés par priorité", counts("Priorité", r.byPriority()));

        PdfPTable technicians = table(new float[] {3, 1.2f, 1, 1.4f, 1.4f, 1.2f}, "Technicien", "Ouverts", "Résolus",
                "Temps moyen", "Respect SLA", "Note");
        for (TechnicianPerformance t : r.byTechnician()) {
            row(technicians, t.fullName(), String.valueOf(t.openAssigned()), String.valueOf(t.resolved()),
                    ReportLabels.number(t.avgResolutionHours(), " h"), ReportLabels.number(t.slaRate(), " %"),
                    ReportLabels.number(t.avgRating(), " / 5"));
        }
        section(document, "Performance par technicien", technicians);

        PdfPTable monthly = table(new float[] {2, 1, 1}, "Mois", "Créés", "Résolus");
        for (TimelinePoint p : r.monthly()) {
            row(monthly, ReportLabels.month(p.bucket()), String.valueOf(p.created()), String.valueOf(p.resolved()));
        }
        section(document, "Évolution mensuelle", monthly);

        document.close();
        return out.toByteArray();
    }

    private static PdfPTable counts(String label, List<CountItem> items) {
        PdfPTable table = table(new float[] {3, 1}, label, "Tickets");
        items.forEach(item -> row(table, item.label(), String.valueOf(item.count())));
        return table;
    }

    /**
     * Title and table in one unbreakable block, so a title never ends a page alone while its table starts on the
     * next one. (A section longer than a page still splits, inside its table.)
     */
    private static void section(Document document, String title, PdfPTable content) {
        PdfPTable block = new PdfPTable(1);
        block.setWidthPercentage(100);
        block.setKeepTogether(true);
        PdfPCell heading = new PdfPCell(new Phrase(title, SECTION));
        heading.setBorder(Rectangle.NO_BORDER);
        heading.setPaddingTop(14);
        heading.setPaddingBottom(6);
        heading.setPaddingLeft(0);
        PdfPCell body = new PdfPCell(content);
        body.setBorder(Rectangle.NO_BORDER);
        body.setPadding(0);
        block.addCell(heading);
        block.addCell(body);
        document.add(block);
    }

    private static PdfPTable table(float[] widths, String... headers) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, HEADER));
            cell.setBackgroundColor(INDIGO);
            cell.setPadding(5);
            cell.setBorderColor(INDIGO);
            table.addCell(cell);
        }
        return table;
    }

    private static void row(PdfPTable table, String... values) {
        boolean striped = (table.getRows().size() % 2) == 0;
        for (int i = 0; i < values.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(values[i], BODY));
            cell.setPadding(4);
            cell.setBorderColor(new Color(0xE2, 0xE8, 0xF0));
            if (striped) {
                cell.setBackgroundColor(STRIPE);
            }
            if (i > 0) {
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            }
            table.addCell(cell);
        }
    }
}
