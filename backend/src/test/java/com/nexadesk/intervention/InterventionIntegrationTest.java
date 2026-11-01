package com.nexadesk.intervention;

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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InterventionIntegrationTest {

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

    private String employee;
    private String assignee;
    private String colleague;
    private String admin;
    private long ticketId;

    @BeforeEach
    void setUp() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        TicketCategory category = categoryRepository.save(new TicketCategory("Interv " + run, null, "tag", null));
        user("emp." + run, RoleCode.EMPLOYEE, null);
        user("tech." + run, RoleCode.TECHNICIAN, category);
        user("tech2." + run, RoleCode.TECHNICIAN, category);
        user("admin." + run, RoleCode.ADMIN, null);
        employee = token("emp." + run);
        assignee = token("tech." + run);
        colleague = token("tech2." + run);
        admin = token("admin." + run);

        ticketId = body(call(employee, post("/api/tickets"), Map.of("type", "INCIDENT",
                "title", "Écran noir au démarrage", "description", "Le poste ne s'allume plus depuis ce matin.",
                "categoryId", category.getId(), "impact", "MEDIUM", "urgency", "MEDIUM"))).get("id").asLong();
        call(assignee, post("/api/tickets/{id}/take", ticketId)).andExpect(status().isOk());
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

    private ResultActions log(String token, Map<String, Object> request) throws Exception {
        return call(token, post("/api/tickets/{id}/interventions", ticketId), request);
    }

    private void start() throws Exception {
        call(assignee, post("/api/tickets/{id}/status", ticketId), Map.of("targetStatus", "IN_PROGRESS"))
                .andExpect(status().isOk());
    }

    @Test
    void interventionsRequireAStartedTicketAndTheAssignee() throws Exception {
        Map<String, Object> request = Map.of("diagnosis", "Alimentation défectueuse", "timeSpentMinutes", 20);

        log(assignee, request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTION_NOT_ALLOWED"));
        start();
        log(colleague, request).andExpect(status().isConflict());
        log(employee, request).andExpect(status().isForbidden());
        call(employee, get("/api/tickets/{id}/interventions", ticketId)).andExpect(status().isForbidden());

        log(assignee, request).andExpect(status().isCreated())
                .andExpect(jsonPath("$.editable").value(true));
        log(admin, Map.of("diagnosis", "Vérification du bloc secteur", "timeSpentMinutes", 10))
                .andExpect(status().isCreated());

        call(colleague, get("/api/tickets/{id}/interventions", ticketId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interventions.length()").value(2))
                .andExpect(jsonPath("$.totalMinutes").value(30))
                .andExpect(jsonPath("$.interventions[0].editable").value(false));
    }

    @Test
    void inputIsValidated() throws Exception {
        start();
        Instant now = Instant.now();

        log(assignee, Map.of("diagnosis", "", "timeSpentMinutes", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
        log(assignee, Map.of("diagnosis", "Trop long", "timeSpentMinutes", 1441))
                .andExpect(status().isBadRequest());
        log(assignee, Map.of("diagnosis", "Période inversée", "timeSpentMinutes", 30,
                "startedAt", now.minus(1, ChronoUnit.HOURS).toString(),
                "endedAt", now.minus(2, ChronoUnit.HOURS).toString()))
                .andExpect(status().isBadRequest());
        log(assignee, Map.of("diagnosis", "Dans le futur", "timeSpentMinutes", 30,
                "startedAt", now.plus(2, ChronoUnit.HOURS).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheAuthorOrAnAdminCanEditAnIntervention() throws Exception {
        start();
        long id = body(log(assignee, Map.of("diagnosis", "Câble HDMI débranché", "timeSpentMinutes", 5)))
                .get("id").asLong();
        Map<String, Object> update = Map.of("diagnosis", "Câble HDMI défectueux, remplacé", "timeSpentMinutes", 15);

        call(colleague, put("/api/tickets/{t}/interventions/{i}", ticketId, id), update)
                .andExpect(status().isConflict());
        call(assignee, put("/api/tickets/{t}/interventions/{i}", ticketId, id), update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeSpentMinutes").value(15))
                .andExpect(jsonPath("$.diagnosis").value("Câble HDMI défectueux, remplacé"));
    }

    @Test
    void resolvingReusesTheLatestInterventionSolution() throws Exception {
        start();
        call(assignee, post("/api/tickets/{id}/status", ticketId), Map.of("targetStatus", "RESOLVED"))
                .andExpect(status().isBadRequest());

        log(assignee, Map.of("diagnosis", "Barrette mémoire mal insérée", "timeSpentMinutes", 25,
                "solution", "Barrette mémoire réinsérée, le poste démarre normalement."))
                .andExpect(status().isCreated());
        call(assignee, post("/api/tickets/{id}/status", ticketId), Map.of("targetStatus", "RESOLVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolution").value("Barrette mémoire réinsérée, le poste démarre normalement."));

        // Closed ticket: interventions become read-only.
        call(employee, post("/api/tickets/{id}/confirm-resolution", ticketId)).andExpect(status().isOk());
        call(assignee, get("/api/tickets/{id}/interventions", ticketId))
                .andExpect(jsonPath("$.interventions[0].editable").value(false));
    }

    @Test
    void techniciansSeeTheirOwnWorkLogWithTotalTime() throws Exception {
        start();
        log(assignee, Map.of("diagnosis", "Premier passage", "timeSpentMinutes", 40)).andExpect(status().isCreated());
        log(assignee, Map.of("diagnosis", "Second passage", "timeSpentMinutes", 35)).andExpect(status().isCreated());

        call(assignee, get("/api/interventions/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes").value(75))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.content[0].diagnosis").value("Second passage"));
        call(colleague, get("/api/interventions/mine")).andExpect(jsonPath("$.totalMinutes").value(0));
        call(employee, get("/api/interventions/mine")).andExpect(status().isForbidden());
    }
}
