package com.nexadesk.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Notifications produced by real ticket actions (after commit), and the notification centre API. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationIntegrationTest {

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
    private String employee;
    private String technician;

    @BeforeEach
    void setUp() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        TicketCategory category = categoryRepository.save(new TicketCategory("Notif " + run, null, "tag", null));
        categoryId = category.getId();
        user("emp." + run, RoleCode.EMPLOYEE, null);
        user("tech." + run, RoleCode.TECHNICIAN, category);
        employee = token("emp." + run);
        technician = token("tech." + run);
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

    @Test
    void ticketActionsNotifyTheRightPeopleAndTheCentreTracksReadState() throws Exception {
        long ticketId = body(call(employee, post("/api/tickets"), Map.of("type", "INCIDENT",
                "title", "Écran qui scintille", "description", "L'écran scintille depuis ce matin.",
                "categoryId", categoryId, "impact", "LOW", "urgency", "MEDIUM"))).get("id").asLong();

        // The specialist is told about the new ticket; the author is not.
        call(technician, get("/api/notifications").param("unreadOnly", "true"))
                .andExpect(jsonPath("$.content[0].type").value("TICKET_CREATED"))
                .andExpect(jsonPath("$.content[0].ticketId").value(ticketId))
                .andExpect(jsonPath("$.content[0].link").value("/tickets/" + ticketId));
        call(employee, get("/api/notifications/unread-count")).andExpect(jsonPath("$.count").value(0));

        call(technician, post("/api/tickets/{id}/take", ticketId)).andExpect(status().isOk());
        call(technician, post("/api/tickets/{id}/comments", ticketId),
                Map.of("body", "Pouvez-vous essayer un autre câble ?", "internal", false));
        call(technician, post("/api/tickets/{id}/comments", ticketId),
                Map.of("body", "Note interne : écran à remplacer probablement", "internal", true));

        JsonNode mine = body(call(employee, get("/api/notifications")));
        // Assignment + public reply; the internal note produces nothing for the requester.
        org.assertj.core.api.Assertions.assertThat(mine.get("totalElements").asInt()).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThat(mine.toString()).contains("TICKET_ASSIGNED", "NEW_COMMENT")
                .doesNotContain("Note interne");

        long firstId = mine.get("content").get(0).get("id").asLong();
        call(employee, post("/api/notifications/{id}/read", firstId)).andExpect(jsonPath("$.read").value(true));
        call(employee, get("/api/notifications/unread-count")).andExpect(jsonPath("$.count").value(1));

        // Someone else's notification is not found, and cannot be marked.
        call(technician, post("/api/notifications/{id}/read", firstId)).andExpect(status().isNotFound());

        call(employee, post("/api/notifications/read-all")).andExpect(jsonPath("$.updated").value(1));
        call(employee, get("/api/notifications/unread-count")).andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void notificationsCannotBeSortedArbitrarily() throws Exception {
        call(employee, get("/api/notifications").param("sort", "recipient.passwordHash"))
                .andExpect(status().isBadRequest());
    }
}
