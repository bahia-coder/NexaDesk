package com.nexadesk.reporting.export;

import com.nexadesk.reporting.dto.ReportDtos.ReportScope;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** French display helpers shared by the Excel and PDF exports. */
public final class ReportLabels {

    static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRANCE);
    static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRANCE);

    private ReportLabels() {
    }

    static String scope(ReportScope s) {
        StringBuilder text = new StringBuilder("Période du ").append(s.from().format(DATE)).append(" au ")
                .append(s.to().format(DATE));
        if (s.categoryName() != null) {
            text.append(" · Catégorie : ").append(s.categoryName());
        }
        if (s.technicianName() != null) {
            text.append(" · Technicien : ").append(s.technicianName());
        }
        return text.toString();
    }

    static String month(LocalDate bucket) {
        String label = bucket.format(MONTH);
        return Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    static String number(Double value, String unit) {
        return value == null ? "—" : String.format(Locale.FRANCE, "%.1f%s", value, unit);
    }

    public static String fileName(ReportScope s, String extension) {
        return "nexadesk-rapport-" + s.from() + "_" + s.to() + "." + extension;
    }
}
