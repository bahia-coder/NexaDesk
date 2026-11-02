package com.nexadesk.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.TestcontainersConfiguration;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.RoleRepository;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Dashboards and reports against PostgreSQL. Each test works in its own category and with its own users, so exact
 * figures can be asserted although the database is shared with the other test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AnalyticsIntegrationTest {

    private static final String PASSWORD = "Integration@2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private TicketCategoryRepository categoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private long categoryId;
    private long technicianId;
    private String employee;
    private String technician;
    private String otherTechnician;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        TicketCategory category = categoryRepository.save(new TicketCategory("Stats " + run, null, "tag", null));
        categoryId = category.getId();
        user("emp." + run, RoleCode.EMPLOYEE, null);
        technicianId = user("tech." + run, RoleCode.TECHNICIAN, category).getId();
        user("tech2." + run, RoleCode.TECHNICIAN, category);
        user("admin." + run, RoleCode.ADMIN, null);
        employee = token("emp." + run);
        technician = token("tech." + run);
        otherTechnician = token("tech2." + run);
        admin = token("admin." + run);
    }

    private User user(String login, RoleCode role, TicketCategory specialty) {
        User user = User.builder().email(login + "@nexadesk.test").passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("IT").lastName(login).role(roleRepository.findByCode(role).orElseThrow()).build();
        if (specialty != null) {
            user.replaceSpecialties(List.of(specialty));
        }
        return userRepository.save(user);
    }

    private String token(String login) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", login + "@nexadesk.test",
                                "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        return call(token, request.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long ticket(String impact, String urgency) throws Exception {
        return body(call(employee, post("/api/tickets"), Map.of("type", "INCIDENT", "title", "Imprimante bloquée",
                "description", "L'imprimante affiche une erreur papier.", "categoryId", categoryId,
                "impact", impact, "urgency", urgency)).andExpect(status().isCreated())).get("id").asLong();
    }

    /** Three tickets: one resolved by the technician, one in progress, one new. */
    private void scenario() throws Exception {
        long resolved = ticket("HIGH", "HIGH");
        long inProgress = ticket("LOW", "LOW");
        ticket("MEDIUM", "MEDIUM");
        for (long id : new long[] {resolved, inProgress}) {
            call(technician, post("/api/tickets/{id}/take", id)).andExpect(status().isOk());
            call(technician, post("/api/tickets/{id}/status", id), Map.of("targetStatus", "IN_PROGRESS"));
        }
        call(technician, post("/api/tickets/{id}/status", resolved), Map.of("targetStatus", "RESOLVED",
                "resolution", "Bac papier réaligné, impression rétablie.")).andExpect(status().isOk());
        call(employee, post("/api/tickets/{id}/feedback", resolved), Map.of("rating", 4)).andExpect(status().isCreated());
    }

    @Test
    void reportComputesExactFiguresForTheFilteredScope() throws Exception {
        scenario();

        call(admin, get("/api/reports/summary").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.created").value(3))
                .andExpect(jsonPath("$.summary.resolved").value(1))
                .andExpect(jsonPath("$.summary.resolutionRate").value(33.3))
                .andExpect(jsonPath("$.summary.sla.resolutionRate").value(100.0))
                .andExpect(jsonPath("$.byPriority.length()").value(4))
                .andExpect(jsonPath("$.byPriority[?(@.key == 'CRITICAL')].count").value(1))
                .andExpect(jsonPath("$.byPriority[?(@.key == 'LOW')].count").value(1))
                .andExpect(jsonPath("$.satisfaction.average").value(4.0))
                .andExpect(jsonPath("$.satisfaction.distribution[3]").value(1))
                .andExpect(jsonPath("$.byTechnician[?(@.id == " + technicianId + ")].resolved").value(1))
                .andExpect(jsonPath("$.byTechnician[?(@.id == " + technicianId + ")].openAssigned").value(1))
                .andExpect(jsonPath("$.monthly.length()").value(Matchers.greaterThanOrEqualTo(3)));

        call(admin, get("/api/reports/summary").param("from", "2026-12-31").param("to", "2026-01-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void techniciansOnlyGetTheirOwnFiguresAndCannotExport() throws Exception {
        scenario();

        call(otherTechnician, get("/api/reports/summary").param("categoryId", String.valueOf(categoryId))
                        .param("technicianId", String.valueOf(technicianId)))
                .andExpect(jsonPath("$.summary.resolved").value(0))
                .andExpect(jsonPath("$.byTechnician.length()").value(1));

        call(technician, get("/api/reports/export.pdf")).andExpect(status().isForbidden());
        call(employee, get("/api/reports/summary")).andExpect(status().isForbidden());
    }

    @Test
    void exportsAreRealExcelAndPdfFiles() throws Exception {
        scenario();

        byte[] xlsx = call(admin, get("/api/reports/export.xlsx").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, Matchers.containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(6);
            assertThat(workbook.getSheet("Synthèse").getRow(4).getCell(1).getNumericCellValue()).isEqualTo(3.0);
        }

        byte[] pdf = call(admin, get("/api/reports/export.pdf").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pdf.length).isGreaterThan(1000);
    }

    @Test
    void roleDashboardsAreComputedForTheCurrentUser() throws Exception {
        scenario();

        call(technician, get("/api/dashboard/technician"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedOpen").value(1))
                .andExpect(jsonPath("$.resolvedThisWeek").value(1))
                .andExpect(jsonPath("$.queue.length()").value(1))
                .andExpect(jsonPath("$.unassignedInSpecialties").value(Matchers.greaterThanOrEqualTo(1)));

        call(employee, get("/api/dashboard/employee"))
                .andExpect(jsonPath("$.waiting").value(1))
                .andExpect(jsonPath("$.inProgress").value(1))
                .andExpect(jsonPath("$.resolved").value(1))
                .andExpect(jsonPath("$.toConfirm").value(1))
                .andExpect(jsonPath("$.awaitingConfirmation.length()").value(1))
                .andExpect(jsonPath("$.recent.length()").value(3));

        JsonNode dashboard = body(call(admin, get("/api/dashboard/admin").param("days", "7")).andExpect(status().isOk()));
        assertThat(dashboard.get("timeline").size()).isEqualTo(7);
        assertThat(dashboard.get("byStatus").size()).isEqualTo(7);
        assertThat(dashboard.get("kpis").get("created").get("value").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(dashboard.get("recentActivity").size()).isEqualTo(10);

        call(admin, get("/api/dashboard/admin").param("days", "12")).andExpect(status().isBadRequest());
        call(employee, get("/api/dashboard/admin")).andExpect(status().isForbidden());
    }
}
