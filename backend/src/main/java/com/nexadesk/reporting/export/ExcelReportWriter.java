package com.nexadesk.reporting.export;

import com.nexadesk.reporting.dto.AnalyticsDtos.CountItem;
import com.nexadesk.reporting.dto.AnalyticsDtos.TechnicianPerformance;
import com.nexadesk.reporting.dto.AnalyticsDtos.TimelinePoint;
import com.nexadesk.reporting.dto.ReportDtos.Report;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * Report as an .xlsx workbook, one sheet per indicator. Numbers are written as numeric cells (sortable,
 * chartable in Excel). Column widths are fixed: auto-sizing needs desktop fonts, absent from the Docker image.
 */
@Component
public class ExcelReportWriter {

    private static final int WIDE = 40 * 256;
    private static final int NARROW = 18 * 256;

    public byte[] write(Report report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            summary(workbook.createSheet("Synthèse"), report, styles);
            counts(workbook.createSheet("Par catégorie"), "Catégorie", report.byCategory(), styles);
            counts(workbook.createSheet("Par priorité"), "Priorité", report.byPriority(), styles);
            technicians(workbook.createSheet("Par technicien"), report.byTechnician(), styles);
            monthly(workbook.createSheet("Évolution mensuelle"), report.monthly(), styles);
            satisfaction(workbook.createSheet("Satisfaction"), report, styles);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private record Styles(CellStyle title, CellStyle header, CellStyle decimal) {

        Styles(Workbook workbook) {
            this(titleStyle(workbook), headerStyle(workbook), decimalStyle(workbook));
        }

        private static CellStyle titleStyle(Workbook wb) {
            Font font = wb.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 14);
            CellStyle style = wb.createCellStyle();
            style.setFont(font);
            return style;
        }

        private static CellStyle headerStyle(Workbook wb) {
            Font font = wb.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            CellStyle style = wb.createCellStyle();
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.INDIGO.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return style;
        }

        private static CellStyle decimalStyle(Workbook wb) {
            CellStyle style = wb.createCellStyle();
            style.setDataFormat(wb.createDataFormat().getFormat("0.0"));
            return style;
        }
    }

    private void summary(Sheet sheet, Report r, Styles styles) {
        sheet.setColumnWidth(0, WIDE);
        sheet.setColumnWidth(1, NARROW);
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue("NexaDesk — Rapport d'activité");
        title.getCell(0).setCellStyle(styles.title());
        sheet.createRow(1).createCell(0).setCellValue(ReportLabels.scope(r.scope()));
        header(sheet, 3, styles, "Indicateur", "Valeur");
        int row = 4;
        row = line(sheet, row, "Tickets créés", r.summary().created(), styles);
        row = line(sheet, row, "Tickets résolus", r.summary().resolved(), styles);
        row = line(sheet, row, "Taux de résolution (%)", r.summary().resolutionRate(), styles);
        row = line(sheet, row, "Temps moyen de résolution (h)", r.summary().avgResolutionHours(), styles);
        row = line(sheet, row, "Respect SLA — première réponse (%)", r.summary().sla().firstResponseRate(), styles);
        row = line(sheet, row, "Respect SLA — résolution (%)", r.summary().sla().resolutionRate(), styles);
        line(sheet, row, "Satisfaction moyenne (/5)", r.satisfaction().average(), styles);
    }

    private void counts(Sheet sheet, String label, List<CountItem> items, Styles styles) {
        sheet.setColumnWidth(0, WIDE);
        sheet.setColumnWidth(1, NARROW);
        header(sheet, 0, styles, label, "Tickets créés");
        int row = 1;
        for (CountItem item : items) {
            row = line(sheet, row, item.label(), item.count(), styles);
        }
    }

    private void technicians(Sheet sheet, List<TechnicianPerformance> technicians, Styles styles) {
        sheet.setColumnWidth(0, WIDE);
        for (int column = 1; column <= 6; column++) {
            sheet.setColumnWidth(column, NARROW);
        }
        header(sheet, 0, styles, "Technicien", "Tickets ouverts", "Résolus", "Temps moyen (h)", "Respect SLA (%)",
                "Note moyenne (/5)", "Temps saisi (h)");
        int rowIndex = 1;
        for (TechnicianPerformance t : technicians) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(t.fullName());
            row.createCell(1).setCellValue(t.openAssigned());
            row.createCell(2).setCellValue(t.resolved());
            decimal(row, 3, t.avgResolutionHours(), styles);
            decimal(row, 4, t.slaRate(), styles);
            decimal(row, 5, t.avgRating(), styles);
            decimal(row, 6, t.minutesLogged() / 60.0, styles);
        }
    }

    private void monthly(Sheet sheet, List<TimelinePoint> points, Styles styles) {
        sheet.setColumnWidth(0, NARROW);
        sheet.setColumnWidth(1, NARROW);
        sheet.setColumnWidth(2, NARROW);
        header(sheet, 0, styles, "Mois", "Créés", "Résolus");
        int rowIndex = 1;
        for (TimelinePoint p : points) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(ReportLabels.month(p.bucket()));
            row.createCell(1).setCellValue(p.created());
            row.createCell(2).setCellValue(p.resolved());
        }
    }

    private void satisfaction(Sheet sheet, Report r, Styles styles) {
        sheet.setColumnWidth(0, NARROW);
        sheet.setColumnWidth(1, NARROW);
        header(sheet, 0, styles, "Note", "Évaluations");
        for (int stars = 1; stars <= 5; stars++) {
            line(sheet, stars, stars + " / 5", r.satisfaction().distribution().get(stars - 1), styles);
        }
    }

    private static void header(Sheet sheet, int rowIndex, Styles styles, String... labels) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < labels.length; i++) {
            row.createCell(i).setCellValue(labels[i]);
            row.getCell(i).setCellStyle(styles.header());
        }
    }

    private static int line(Sheet sheet, int rowIndex, String label, long value, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
        return rowIndex + 1;
    }

    private static int line(Sheet sheet, int rowIndex, String label, Double value, Styles styles) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(label);
        decimal(row, 1, value, styles);
        return rowIndex + 1;
    }

    /** Empty cell (not 0) when the value could not be measured. */
    private static void decimal(Row row, int column, Double value, Styles styles) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
            row.getCell(column).setCellStyle(styles.decimal());
        }
    }
}
