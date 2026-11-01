package com.nexadesk.sla;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SlaIntegrationTest {

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
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private SlaJobs slaJobs;

    private long categoryId;
    private String employee;
    private String technician;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        TicketCategory category = categoryRepository.save(new TicketCategory("SLA " + run, null, "tag", null));
        categoryId = category.getId();
        user("emp." + run, RoleCode.EMPLOYEE, null);
        user("tech." + run, RoleCode.TECHNICIAN, category);
        user("admin." + run, RoleCode.ADMIN, null);
        employee = token("emp." + run);
        technician = token("tech." + run);
        admin = token("admin." + run);
    }

    private void user(String login, RoleCode role, TicketCategory specialty) {
        User user = User.builder().email(login + "@nexadesk.test").passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("IT").lastName(login).role(roleRepository.findByCode(role).orElseThrow()).build();
        if (specialty != null) {
            user.replaceSpecialties(List.of(specialty));
        }
        userRepository.save(user);
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

    private long createTicket(String impact, String urgency) throws Exception {
        return body(call(employee, post("/api/tickets"), Map.of("type", "INCIDENT",
                "title", "Imprimante hors service", "description", "Plus aucune impression possible ce matin.",
                "categoryId", categoryId, "impact", impact, "urgency", urgency))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private void setTicketTime(long id, String column, Instant value) {
        jdbc.sql("update tickets set " + column + " = :value where id = :id")
                .param("value", value.atOffset(ZoneOffset.UTC)).param("id", id).update();
    }

    @Test
    void newTicketsGetDeadlinesFromTheirPriorityRule() throws Exception {
        long id = createTicket("HIGH", "HIGH");

        JsonNode sla = body(call(employee, get("/api/tickets/{id}", id))
                .andExpect(jsonPath("$.priority.code").value("CRITICAL"))
                .andExpect(jsonPath("$.sla.state").value("ON_TRACK"))
                .andExpect(jsonPath("$.sla.businessHoursOnly").value(false))).get("sla");

        Instant created = Instant.parse(body(call(employee, get("/api/tickets/{id}", id))).get("createdAt").asText());
        assertThat(Duration.between(created, Instant.parse(sla.get("firstResponseDueAt").asText())))
                .isEqualTo(Duration.ofMinutes(15));
        assertThat(Duration.between(created, Instant.parse(sla.get("resolutionDueAt").asText())))
                .isEqualTo(Duration.ofMinutes(120));
    }

    @Test
    void overdueTicketsAreListedAndReportedOnceToStaffOnly() throws Exception {
        long id = createTicket("LOW", "LOW");
        setTicketTime(id, "resolution_due_at", Instant.now().minus(Duration.ofHours(1)));

        call(technician, get("/api/tickets").param("categoryId", String.valueOf(categoryId)).param("sla", "BREACHED"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sla.state").value("BREACHED"));

        slaJobs.checkDeadlines();
        slaJobs.checkDeadlines();

        String staffHistory = call(technician, get("/api/tickets/{id}/history", id))
                .andReturn().getResponse().getContentAsString();
        assertThat(staffHistory.split("SLA_BREACHED", -1)).as("exactly one alert").hasSize(2);
        call(employee, get("/api/tickets/{id}/history", id))
                .andExpect(jsonPath("$[*].action", not(hasItem("SLA_BREACHED"))));
    }

    @Test
    void resolvedTicketsLeftUnansweredAreClosedAutomatically() throws Exception {
        long id = createTicket("MEDIUM", "MEDIUM");
        call(technician, post("/api/tickets/{id}/take", id)).andExpect(status().isOk());
        call(technician, post("/api/tickets/{id}/status", id), Map.of("targetStatus", "IN_PROGRESS"));
        call(technician, post("/api/tickets/{id}/status", id), Map.of("targetStatus", "RESOLVED",
                "resolution", "Pilote d'impression réinstallé."))
                .andExpect(jsonPath("$.sla.state").value("MET"));

        slaJobs.autoCloseResolved();
        call(employee, get("/api/tickets/{id}", id)).andExpect(jsonPath("$.status").value("RESOLVED"));

        setTicketTime(id, "resolved_at", Instant.now().minus(Duration.ofDays(30)));
        slaJobs.autoCloseResolved();
        call(employee, get("/api/tickets/{id}", id))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.availableActions", hasItem("REOPEN")));
    }

    @Test
    void administratorManagesRulesCalendarAndHolidays() throws Exception {
        JsonNode rules = body(call(technician, get("/api/sla/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].priority").value("LOW"))
                .andExpect(jsonPath("$[3].businessHoursOnly").value(false)));
        long lowRuleId = rules.get(0).get("id").asLong();

        call(technician, put("/api/sla/rules/{id}", lowRuleId), Map.of("firstResponseMinutes", 60,
                "resolutionMinutes", 600, "businessHoursOnly", true, "warningThresholdPercent", 80))
                .andExpect(status().isForbidden());
        call(admin, put("/api/sla/rules/{id}", lowRuleId), Map.of("firstResponseMinutes", 600,
                "resolutionMinutes", 300, "businessHoursOnly", true, "warningThresholdPercent", 80))
                .andExpect(status().isBadRequest());
        call(admin, put("/api/sla/rules/{id}", lowRuleId), Map.of("firstResponseMinutes", 480,
                "resolutionMinutes", 2880, "businessHoursOnly", true, "warningThresholdPercent", 99))
                .andExpect(status().isBadRequest());

        call(admin, put("/api/sla/calendar"), Map.of("workStart", "18:00", "workEnd", "08:00",
                "workingDays", List.of("MONDAY")))
                .andExpect(status().isBadRequest());
        call(admin, get("/api/sla/calendar"))
                .andExpect(jsonPath("$.workStart").value("08:00:00"))
                .andExpect(jsonPath("$.workingDays.length()").value(5));

        long holidayId = body(call(admin, post("/api/sla/holidays"), Map.of("day", "2031-12-24", "label", "Veille de Noël"))
                .andExpect(status().isCreated())).get("id").asLong();
        call(admin, post("/api/sla/holidays"), Map.of("day", "2031-12-24", "label", "Doublon"))
                .andExpect(status().isConflict());
        call(admin, delete("/api/sla/holidays/{id}", holidayId)).andExpect(status().isNoContent());
    }

    @Test
    void previewComputesDeadlinesInBusinessHours() throws Exception {
        // Monday 2 Nov 2026, 16:00 in Paris (15:00 UTC): 2 h on Monday + 6 h on Tuesday.
        call(admin, get("/api/sla/preview").param("priority", "HIGH").param("from", "2026-11-02T15:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstResponseDueAt").value("2026-11-02T16:00:00Z"))
                .andExpect(jsonPath("$.resolutionDueAt").value("2026-11-03T13:00:00Z"));
    }
}
