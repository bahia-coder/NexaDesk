package com.nexadesk.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Knowledge base and AI endpoints against PostgreSQL (French full-text search, trigrams), in the default
 * heuristic mode. Each test uses a made-up word of its own so search results are exact although the database is
 * shared with the other test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class KnowledgeAndAiIntegrationTest {

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

    private String word;
    private long categoryId;
    private String employee;
    private String otherEmployee;
    private String technician;
    private String otherTechnician;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        String run = UUID.randomUUID().toString().substring(0, 8);
        // Letters only (each hex digit → a..p): a unique lexeme the French stemmer leaves alone.
        StringBuilder letters = new StringBuilder("kb");
        run.chars().forEach(c -> letters.append((char) ('a' + Character.digit(c, 16))));
        word = letters.append('x').toString();
        TicketCategory category = categoryRepository.save(new TicketCategory("KB " + run, "Tests", "tag",
                word + ", photocopieuse"));
        categoryId = category.getId();
        user("emp." + run, RoleCode.EMPLOYEE, null);
        user("emp2." + run, RoleCode.EMPLOYEE, null);
        user("tech." + run, RoleCode.TECHNICIAN, category);
        user("tech2." + run, RoleCode.TECHNICIAN, null);
        user("admin." + run, RoleCode.ADMIN, null);
        employee = token("emp." + run);
        otherEmployee = token("emp2." + run);
        technician = token("tech." + run);
        otherTechnician = token("tech2." + run);
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

    private long searchCount(String token, String text) throws Exception {
        return body(call(token, get("/api/knowledge").param("search", text)).andExpect(status().isOk()))
                .get("totalElements").asLong();
    }

    private JsonNode article(String token, String title) throws Exception {
        return body(call(token, post("/api/knowledge"), Map.of("title", title,
                "summary", "Procédure pour la photocopieuse " + word,
                "content", "Ouvrez le capot de la photocopieuse " + word + " et retirez la feuille coincée.",
                "categoryId", categoryId)).andExpect(status().isCreated()));
    }

    private long ticket(String token, String title) throws Exception {
        return body(call(token, post("/api/tickets"), Map.of("type", "INCIDENT", "title", title,
                "description", "La photocopieuse " + word + " affiche une erreur de bourrage.",
                "categoryId", categoryId, "impact", "LOW", "urgency", "MEDIUM"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void articleLifecycleDrivesWhoFindsIt() throws Exception {
        JsonNode draft = article(technician, "Débloquer la photocopieuse " + word);
        long id = draft.get("id").asLong();

        assertThat(searchCount(technician, word)).as("the author finds their draft").isEqualTo(1);
        assertThat(searchCount(employee, word)).isZero();
        assertThat(searchCount(otherTechnician, word)).isZero();
        call(employee, get("/api/knowledge/{id}", id)).andExpect(status().isNotFound());
        call(technician, post("/api/knowledge/{id}/publish", id)).andExpect(status().isForbidden());

        call(admin, post("/api/knowledge/{id}/publish", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertThat(searchCount(employee, word)).isEqualTo(1);
        assertThat(searchCount(employee, word + " feuilles coincées")).as("French stemming").isEqualTo(1);

        call(employee, post("/api/knowledge/{id}/helpful", id)).andExpect(jsonPath("$.helpfulCount").value(1));
        call(employee, post("/api/knowledge/{id}/helpful", id)).andExpect(jsonPath("$.helpfulCount").value(1));
        call(otherEmployee, get("/api/knowledge/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(1))
                .andExpect(jsonPath("$.helpfulCount").value(1))
                .andExpect(jsonPath("$.helpedByMe").value(false))
                .andExpect(jsonPath("$.availableActions[0]").value("MARK_HELPFUL"));

        // The author may no longer edit once published; a stale version is refused.
        Map<String, Object> edit = Map.of("title", "Nouveau titre " + word, "summary", "Résumé",
                "content", "Un contenu suffisamment long pour l'article.", "categoryId", categoryId,
                "version", draft.get("version").asInt());
        call(technician, put("/api/knowledge/{id}", id), edit).andExpect(status().isConflict());
        call(admin, put("/api/knowledge/{id}", id), edit).andExpect(status().isConflict());

        call(admin, post("/api/knowledge/{id}/archive", id)).andExpect(status().isOk());
        assertThat(searchCount(employee, word)).isZero();
        call(employee, post("/api/knowledge/{id}/helpful", id)).andExpect(status().isNotFound());
    }

    @Test
    void solvedTicketsBecomeDraftsAndArticlesAreLinkedToTickets() throws Exception {
        long id = ticket(employee, "Photocopieuse " + word + " bloquée");
        call(technician, post("/api/knowledge/from-ticket/{id}", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTION_NOT_ALLOWED"));

        call(technician, post("/api/tickets/{id}/take", id)).andExpect(status().isOk());
        call(technician, post("/api/tickets/{id}/status", id), Map.of("targetStatus", "IN_PROGRESS"));
        call(technician, post("/api/tickets/{id}/status", id), Map.of("targetStatus", "RESOLVED",
                "resolution", "Rouleau d'entraînement remplacé.")).andExpect(status().isOk());

        JsonNode draft = body(call(technician, post("/api/knowledge/from-ticket/{id}", id))
                .andExpect(status().isCreated()));
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.get("content").asText()).contains("## Solution", "Rouleau d'entraînement remplacé.");
        assertThat(draft.get("sourceTicket").get("id").asLong()).isEqualTo(id);
        assertThat(draft.get("linkedTickets").asLong()).isEqualTo(1);
        call(technician, post("/api/knowledge/from-ticket/{id}", id)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_ARTICLE_EXISTS"));

        // The requester does not see a draft among the ticket's articles; a colleague cannot see the ticket.
        call(technician, get("/api/tickets/{id}/knowledge-links", id)).andExpect(jsonPath("$.length()").value(1));
        call(employee, get("/api/tickets/{id}/knowledge-links", id)).andExpect(jsonPath("$.length()").value(0));
        call(otherEmployee, get("/api/tickets/{id}/knowledge-links", id)).andExpect(status().isNotFound());

        // Only published articles can be linked; replacing the list removes the old links.
        long articleId = draft.get("id").asLong();
        call(technician, put("/api/tickets/{id}/knowledge-links", id), Map.of("articleIds", List.of(articleId)))
                .andExpect(status().isBadRequest());
        call(admin, post("/api/knowledge/{id}/publish", articleId)).andExpect(status().isOk());
        long other = article(admin, "Autre procédure " + word).get("id").asLong();
        call(admin, post("/api/knowledge/{id}/publish", other)).andExpect(status().isOk());
        call(technician, put("/api/tickets/{id}/knowledge-links", id), Map.of("articleIds", List.of(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(other));
        call(otherTechnician, put("/api/tickets/{id}/knowledge-links", id), Map.of("articleIds", List.of()))
                .andExpect(status().isNotFound());
        call(employee, get("/api/tickets/{id}/knowledge-links", id)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void similarTicketsRespectTicketVisibility() throws Exception {
        long mine = ticket(employee, "Photocopieuse " + word + " en panne");
        Map<String, String> draft = Map.of("title", "Photocopieuse " + word + " en erreur",
                "description", "Encore un bourrage sur la photocopieuse " + word);

        assertThat(body(call(employee, post("/api/ai/similar"), draft)).get("tickets").get(0).get("ticket")
                .get("id").asLong()).isEqualTo(mine);
        assertThat(body(call(otherEmployee, post("/api/ai/similar"), draft)).get("tickets")).isEmpty();
        assertThat(body(call(technician, post("/api/ai/similar"), draft)).get("tickets")).isNotEmpty();

        long second = ticket(otherEmployee, "Erreur de bourrage photocopieuse " + word);
        JsonNode similar = body(call(technician, get("/api/ai/tickets/{id}/similar", second)).andExpect(status().isOk()));
        assertThat(similar.get("tickets").get(0).get("ticket").get("id").asLong()).isEqualTo(mine);
        assertThat(similar.get("tickets").get(0).get("score").asInt()).isEqualTo(100);
        call(otherTechnician, get("/api/ai/tickets/{id}/similar", second)).andExpect(status().isNotFound());
        call(otherEmployee, get("/api/ai/tickets/{id}/similar", second)).andExpect(status().isForbidden());
    }

    @Test
    void heuristicModeIsDeclaredOnEveryAnswer() throws Exception {
        call(employee, get("/api/ai/status")).andExpect(jsonPath("$.mode").value("HEURISTIC"))
                .andExpect(jsonPath("$.provider").doesNotExist());

        call(employee, post("/api/ai/classify"), Map.of("title", "La " + word + " est bloquée",
                        "description", "Tout le service attend, urgent."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("HEURISTIC"))
                .andExpect(jsonPath("$.category.id").value(categoryId))
                .andExpect(jsonPath("$.impact").value("HIGH"))
                .andExpect(jsonPath("$.urgency").value("HIGH"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"));

        long id = ticket(employee, "Photocopieuse " + word + " muette");
        call(technician, get("/api/ai/tickets/{id}/summary", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("HEURISTIC"))
                .andExpect(jsonPath("$.disclaimer").value("Résumé automatique (non IA) : faits extraits du ticket."));
        call(employee, get("/api/ai/tickets/{id}/summary", id)).andExpect(status().isForbidden());
        call(technician, get("/api/ai/tickets/{id}/suggestions", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.steps.length()").value(0));

        long articleId = article(technician, "Photocopieuse " + word + " : bourrage").get("id").asLong();
        call(admin, post("/api/knowledge/{id}/publish", articleId)).andExpect(status().isOk());
        JsonNode answer = body(call(employee, post("/api/ai/assistant"),
                Map.of("question", "Que faire quand la photocopieuse " + word + " bourre ?"))
                .andExpect(status().isOk()));
        assertThat(answer.get("source").asText()).isEqualTo("HEURISTIC");
        assertThat(answer.get("answer").isNull()).as("no model, no generated text").isTrue();
        assertThat(answer.get("articles").get(0).get("article").get("id").asLong()).isEqualTo(articleId);

        call(employee, post("/api/ai/assistant"), Map.of("question", "")).andExpect(status().isBadRequest());
    }

    @Test
    void globalSearchFollowsEachListsRules() throws Exception {
        ticket(employee, "Photocopieuse " + word + " introuvable");

        JsonNode own = body(call(employee, get("/api/search").param("q", word)).andExpect(status().isOk()));
        assertThat(own.get("tickets")).hasSize(1);
        assertThat(own.get("users")).isEmpty();
        assertThat(body(call(otherEmployee, get("/api/search").param("q", word))).get("tickets")).isEmpty();
        assertThat(body(call(admin, get("/api/search").param("q", word))).get("tickets")).hasSize(1);
        call(admin, get("/api/search").param("q", "x")).andExpect(status().isBadRequest());
    }
}
