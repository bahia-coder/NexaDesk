package com.nexadesk.reporting;

import com.nexadesk.reporting.dto.ReportDtos.Report;
import com.nexadesk.reporting.dto.ReportDtos.ReportRequest;
import com.nexadesk.reporting.export.ExcelReportWriter;
import com.nexadesk.reporting.export.PdfReportWriter;
import com.nexadesk.reporting.export.ReportLabels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Rapports", description = "Rapport d'activité filtrable et exports Excel / PDF")
public class ReportController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService service;
    private final ExcelReportWriter excel;
    private final PdfReportWriter pdf;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
    @Operation(summary = "Rapport d'activité",
            description = "Par défaut les 90 derniers jours. Filtres : from, to, categoryId, technicianId. "
                    + "Un technicien obtient toujours ses propres chiffres.")
    public Report summary(@ParameterObject ReportRequest request) {
        return service.build(request);
    }

    @GetMapping("/export.xlsx")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exporter le rapport en Excel")
    public ResponseEntity<byte[]> exportExcel(@ParameterObject ReportRequest request) {
        Report report = service.build(request);
        return file(excel.write(report), XLSX, ReportLabels.fileName(report.scope(), "xlsx"));
    }

    @GetMapping("/export.pdf")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exporter le rapport en PDF")
    public ResponseEntity<byte[]> exportPdf(@ParameterObject ReportRequest request) {
        Report report = service.build(request);
        return file(pdf.write(report), MediaType.APPLICATION_PDF, ReportLabels.fileName(report.scope(), "pdf"));
    }

    private static ResponseEntity<byte[]> file(byte[] content, MediaType type, String name) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(content);
    }
}
