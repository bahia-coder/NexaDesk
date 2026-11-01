package com.nexadesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.TestcontainersConfiguration;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.support.TestFiles;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.RoleRepository;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.util.HashMap;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * End-to-end ticket scenarios with real JWTs against PostgreSQL. Each test runs in its own fresh category
 * and with fresh users, so tests are independent of each other and of the other test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TicketLifecycleIntegrationTest {

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
    private String otherEmployee;
    private String specialist;
    private String outsider;
    private String admin;
    private long specialistId;
    private long employeeId;

    @BeforeEach
    void setUp() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        TicketCategory category = new TicketCategory("Cat " + run, null, "tag", null);
        category.addSubcategory("Sub " + run);
        categoryId = categoryRepository.save(category).getId();

        employeeId = user("emp." + run, RoleCode.EMPLOYEE, null).getId();
        user("emp2." + run, RoleCode.EMPLOYEE, null);
        specialistId = user("tech." + run, RoleCode.TECHNICIAN, category).getId();
        user("out." + run, RoleCode.TECHNICIAN, null);
        user("admin." + run, RoleCode.ADMIN, null);

        employee = token("emp." + run);
        otherEmployee = token("emp2." + run);
        specialist = token("tech." + run);
        outsider = token("out." + run);
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
                        .content(json(Map.of("email", login + "@nexadesk.test", "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        return call(token, request.contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long createTicket(String token, String impact, String urgency) throws Exception {
        return body(call(token, post("/api/tickets"), Map.of(
                "type", "INCIDENT",
                "title", "VPN inaccessible depuis le domicile",
                "description", "Erreur 809 à la connexion depuis ce matin.",
                "categoryId", categoryId,
                "impact", impact,
                "urgency", urgency)).andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions changeStatus(String token, long id, Map<String, Object> request) throws Exception {
        return call(token, post("/api/tickets/{id}/status", id), request);
    }

    @Test
    void fullLifecycleFromCreationToReopening() throws Exception {
        long id = body(call(employee, post("/api/tickets"), Map.of(
                "type", "INCIDENT",
                "title", "VPN inaccessible depuis le domicile",
                "description", "Erreur 809 à la connexion depuis ce matin.",
                "categoryId", categoryId,
                "impact", "HIGH",
                "urgency", "HIGH"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference", org.hamcrest.Matchers.matchesPattern("INC-\\d{4}-\\d{4}")))
                .andExpect(jsonPath("$.priority.code").value("CRITICAL"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.availableActions", hasItem("EDIT")))
                .andExpect(jsonPath("$.availableActions", not(hasItem("TAKE"))))).get("id").asLong();

        // Visibility: hidden (404) from another employee and from a technician of another specialty.
        call(otherEmployee, get("/api/tickets/{id}", id)).andExpect(status().isNotFound());
        call(outsider, get("/api/tickets/{id}", id)).andExpect(status().isNotFound());
        call(specialist, get("/api/tickets/{id}", id))
                .andExpect(jsonPath("$.availableActions", hasItem("TAKE")));

        call(specialist, post("/api/tickets/{id}/take", id))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignee.id").value(specialistId));
        changeStatus(specialist, id, Map.of("targetStatus", "IN_PROGRESS"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Internal notes never reach the requester.
        call(specialist, post("/api/tickets/{id}/comments", id),
                Map.of("body", "Certificat de la passerelle probablement expiré", "internal", true))
                .andExpect(status().isCreated());
        call(employee, post("/api/tickets/{id}/comments", id), Map.of("body", "Merci !", "internal", true))
                .andExpect(status().isConflict());
        call(employee, get("/api/tickets/{id}/comments", id)).andExpect(jsonPath("$.length()").value(0));
        call(specialist, get("/api/tickets/{id}/comments", id)).andExpect(jsonPath("$.length()").value(1));

        // Waiting for the user, who answers: the ticket resumes by itself.
        changeStatus(specialist, id, Map.of("targetStatus", "PENDING", "pendingReason", "WAITING_USER"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        call(employee, post("/api/tickets/{id}/comments", id),
                Map.of("body", "Voici le message exact : erreur 809.", "internal", false))
                .andExpect(status().isCreated());
        call(employee, get("/api/tickets/{id}", id)).andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        changeStatus(specialist, id, Map.of("targetStatus", "RESOLVED"))
                .andExpect(status().isBadRequest());
        changeStatus(specialist, id, Map.of("targetStatus", "RESOLVED",
                "resolution", "Certificat de la passerelle VPN renouvelé, connexion rétablie."))
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        call(employee, post("/api/tickets/{id}/confirm-resolution", id))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.availableActions", hasItem("REOPEN")));

        // A closed ticket is read-only.
        call(admin, put("/api/tickets/{id}", id), Map.of("title", "Nouveau titre", "description",
                "Nouvelle description", "version", 99))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTION_NOT_ALLOWED"));

        call(employee, post("/api/tickets/{id}/feedback", id), Map.of("rating", 5, "comment", "Rapide et clair"))
                .andExpect(status().isCreated());
        call(employee, post("/api/tickets/{id}/feedback", id), Map.of("rating", 1))
                .andExpect(status().isConflict());

        call(employee, post("/api/tickets/{id}/reopen", id), Map.of("reason", "Le problème est revenu"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.reopenCount").value(1))
                .andExpect(jsonPath("$.assignee.id").value(specialistId));

        // The requester's timeline hides internal entries; staff sees them.
        String requesterHistory = call(employee, get("/api/tickets/{id}/history", id))
                .andReturn().getResponse().getContentAsString();
        assertThat(requesterHistory).contains("CREATED", "ASSIGNED", "STATUS_CHANGED", "REOPENED", "FEEDBACK_ADDED")
                .doesNotContain("Note interne");
        call(specialist, get("/api/tickets/{id}/history", id))
                .andExpect(content().string(containsString("Note interne")));
    }

    @Test
    void optimisticLockingRejectsAnEditBasedOnAStaleVersion() throws Exception {
        long id = createTicket(employee, "LOW", "LOW");

        int newVersion = body(call(employee, put("/api/tickets/{id}", id), Map.of(
                "title", "VPN inaccessible (mis à jour)", "description", "Erreur 809, précisions ajoutées.",
                "version", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority.code").value("LOW"))).get("version").asInt();
        assertThat(newVersion).isEqualTo(1);

        call(employee, put("/api/tickets/{id}", id), Map.of(
                "title", "Autre titre concurrent", "description", "Écrit depuis une copie périmée.", "version", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void listsOnlyShowVisibleTicketsAndCombineFilters() throws Exception {
        long mine = createTicket(employee, "HIGH", "HIGH");
        createTicket(otherEmployee, "LOW", "LOW");

        call(employee, get("/api/tickets").param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine));

        call(specialist, get("/api/tickets").param("categoryId", String.valueOf(categoryId))
                        .param("scope", "UNASSIGNED").param("priority", "CRITICAL")
                        .param("sort", "priority.rank,desc"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].priority.code").value("CRITICAL"));

        call(outsider, get("/api/tickets").param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.totalElements").value(0));

        call(admin, get("/api/tickets/board").param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$.NEW.length()").value(2))
                .andExpect(jsonPath("$.IN_PROGRESS.length()").value(0));

        call(employee, get("/api/tickets").param("sort", "description"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void adminAssignmentAndRejectionRules() throws Exception {
        long id = createTicket(employee, "MEDIUM", "LOW");

        call(employee, post("/api/tickets/{id}/status", id), Map.of("targetStatus", "IN_PROGRESS"))
                .andExpect(status().isForbidden());
        call(admin, post("/api/tickets/{id}/assign", id), Map.of("technicianId", employeeId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ASSIGNEE"));
        call(admin, post("/api/tickets/{id}/assign", id), Map.of("technicianId", specialistId))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        Map<String, Object> rejectWithoutReason = new HashMap<>();
        rejectWithoutReason.put("targetStatus", "REJECTED");
        changeStatus(admin, id, rejectWithoutReason).andExpect(status().isBadRequest());
        changeStatus(admin, id, Map.of("targetStatus", "REJECTED", "reason", "Doublon de INC-2026-0001"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Doublon de INC-2026-0001"));
        call(admin, post("/api/tickets/{id}/reopen", id), Map.of("reason", "Essai"))
                .andExpect(status().isConflict());
    }

    @Test
    void referenceDataInUseIsProtected() throws Exception {
        long id = createTicket(employee, "LOW", "MEDIUM");
        call(admin, post("/api/tickets/{id}/assign", id), Map.of("technicianId", specialistId))
                .andExpect(status().isOk());

        call(admin, post("/api/categories/{id}/deactivate", categoryId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        call(admin, post("/api/users/{id}/deactivate", specialistId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TECHNICIAN_HAS_OPEN_TICKETS"));
        call(admin, get("/api/users/technicians").param("categoryId", String.valueOf(categoryId)))
                .andExpect(jsonPath("$[0].openTickets").value(1));
    }

    @Test
    void attachmentsAreTypeCheckedLimitedAndOnlyReachableByAuthorizedUsers() throws Exception {
        long id = createTicket(employee, "LOW", "LOW");
        MockMultipartFile png = new MockMultipartFile("file", "capture.png", "image/png", TestFiles.PNG);

        long attachmentId = body(call(employee, multipart("/api/tickets/{id}/attachments", id).file(png))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"))).get("id").asLong();

        MockMultipartFile disguised = new MockMultipartFile("file", "facture.pdf", "application/pdf", TestFiles.EXE);
        call(employee, multipart("/api/tickets/{id}/attachments", id).file(disguised))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));

        call(specialist, get("/api/tickets/{id}/attachments/{a}", id, attachmentId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(content().bytes(TestFiles.PNG));
        call(otherEmployee, get("/api/tickets/{id}/attachments/{a}", id, attachmentId))
                .andExpect(status().isNotFound());

        for (int i = 0; i < 4; i++) {
            call(employee, multipart("/api/tickets/{id}/attachments", id).file(png)).andExpect(status().isCreated());
        }
        call(employee, multipart("/api/tickets/{id}/attachments", id).file(png))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_LIMIT"));

        call(employee, delete("/api/tickets/{id}/attachments/{a}", id, attachmentId))
                .andExpect(status().isNoContent());
        call(employee, get("/api/tickets/{id}/attachments", id)).andExpect(jsonPath("$.length()").value(4));
    }
}
