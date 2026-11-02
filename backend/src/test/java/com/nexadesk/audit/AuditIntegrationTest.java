package com.nexadesk.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.TestcontainersConfiguration;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.RoleRepository;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
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
class AuditIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    private String run;
    private String admin;
    private long employeeId;

    @BeforeEach
    void setUp() throws Exception {
        run = UUID.randomUUID().toString().substring(0, 8);
        user("admin." + run, RoleCode.ADMIN);
        employeeId = user("emp." + run, RoleCode.EMPLOYEE).getId();
        admin = token("admin." + run, PASSWORD);
    }

    private User user(String login, RoleCode role) {
        return userRepository.save(User.builder().email(login + "@nexadesk.test")
                .passwordHash(passwordEncoder.encode(PASSWORD)).firstName("IT").lastName(login)
                .role(roleRepository.findByCode(role).orElseThrow()).build());
    }

    private ResultActions login(String login, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "203.0.113.7")
                .content(objectMapper.writeValueAsString(Map.of("email", login + "@nexadesk.test",
                        "password", password))));
    }

    private String token(String login, String password) throws Exception {
        return objectMapper.readTree(login(login, password).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private JsonNode audit(String action, String entityId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/audit").param("action", action);
        if (entityId != null) {
            request.param("entityId", entityId);
        }
        return objectMapper.readTree(call(admin, request).andReturn().getResponse().getContentAsString());
    }

    @Test
    void loginsAreAuditedWithTheReasonAndTheClientAddress() throws Exception {
        login("emp." + run, "wrong-password").andExpect(status().isUnauthorized());
        login("nobody." + run, "whatever").andExpect(status().isUnauthorized());
        login("emp." + run, PASSWORD).andExpect(status().isOk());

        JsonNode failures = audit("LOGIN_FAILURE", String.valueOf(employeeId));
        assertThat(failures.get("totalElements").asInt()).isEqualTo(1);
        assertThat(failures.get("content").get(0).get("summary").asText()).contains("mot de passe incorrect");
        assertThat(failures.get("content").get(0).get("ipAddress").asText()).isEqualTo("203.0.113.7");
        assertThat(failures.get("content").get(0).get("traceId").asText()).isNotBlank();

        // Unknown account: no user to link, but the attempted e-mail is kept.
        JsonNode unknown = audit("LOGIN_FAILURE", null);
        assertThat(unknown.toString()).contains("nobody." + run + "@nexadesk.test");

        assertThat(audit("LOGIN_SUCCESS", String.valueOf(employeeId)).get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void userChangesRecordTheActorAndTheOldAndNewValues() throws Exception {
        call(admin, put("/api/users/{id}", employeeId).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", "emp." + run + "@nexadesk.test",
                        "firstName", "IT", "lastName", "emp." + run, "jobTitle", "Comptable"))))
                .andExpect(status().isOk());

        JsonNode entry = audit("USER_UPDATED", String.valueOf(employeeId)).get("content").get(0);
        assertThat(entry.get("actorEmail").asText()).isEqualTo("admin." + run + "@nexadesk.test");
        assertThat(entry.get("oldValue").get("jobTitle").isNull()).isTrue();
        assertThat(entry.get("newValue").get("jobTitle").asText()).isEqualTo("Comptable");
        assertThat(entry.get("newValue").has("passwordHash")).as("never audit secrets").isFalse();
    }

    @Test
    void ticketActionsAreCopiedToTheAuditTrail() throws Exception {
        String employee = token("emp." + run, PASSWORD);
        JsonNode categories = objectMapper.readTree(call(admin, post("/api/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Audit " + run))))
                .andReturn().getResponse().getContentAsString());
        String reference = objectMapper.readTree(call(employee, post("/api/tickets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("type", "INCIDENT", "title", "Clavier défaillant",
                        "description", "Plusieurs touches ne répondent plus.", "categoryId",
                        categories.get("id").asLong(), "impact", "LOW", "urgency", "LOW"))))
                .andReturn().getResponse().getContentAsString()).get("reference").asText();

        assertThat(audit("TICKET_CREATED", reference).get("totalElements").asInt()).isEqualTo(1);
        assertThat(audit("CATEGORY_CREATED", categories.get("id").asText()).get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void onlyAdministratorsCanReadTheAuditTrail() throws Exception {
        call(token("emp." + run, PASSWORD), get("/api/audit")).andExpect(status().isForbidden());
        call(admin, get("/api/audit/actions")).andExpect(jsonPath("$.length()").value(AuditAction.values().length));
    }
}
