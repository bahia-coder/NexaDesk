package com.nexadesk.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.ai.HeuristicClassifier.CategoryOption;
import com.nexadesk.ai.HeuristicClassifier.SubOption;
import com.nexadesk.ai.dto.AiDtos.AssistantAnswerDto;
import com.nexadesk.ai.dto.AiDtos.AssistantRequest;
import com.nexadesk.ai.dto.AiDtos.ChatRole;
import com.nexadesk.ai.dto.AiDtos.ChatTurn;
import com.nexadesk.ai.dto.AiDtos.ClassificationDto;
import com.nexadesk.ai.dto.AiDtos.TextRequest;
import com.nexadesk.ai.llm.LlmClient;
import com.nexadesk.ai.llm.LlmException;
import com.nexadesk.category.Level;
import com.nexadesk.category.PriorityCode;
import com.nexadesk.intervention.InterventionService;
import com.nexadesk.knowledge.ArticleStatus;
import com.nexadesk.knowledge.KnowledgeService.RelatedArticle;
import com.nexadesk.knowledge.dto.KnowledgeDtos.ArticleSummaryDto;
import com.nexadesk.security.CurrentUser;
import com.nexadesk.ticket.TicketConversationService;
import com.nexadesk.ticket.TicketService;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Model path, validation and fallback rules of {@link AiService}, with a scripted fake model. */
@ExtendWith(MockitoExtension.class)
class AiServiceTest {

    private static final CategoryOption PRINTING = new CategoryOption(1L, "Impression", null, "printer",
            List.of("imprimante"), List.of(new SubOption(10L, "Imprimante")));

    @Mock
    private AiCatalog catalog;
    @Mock
    private SimilarityService similarity;
    @Mock
    private TicketService tickets;
    @Mock
    private TicketConversationService conversation;
    @Mock
    private InterventionService interventions;
    @Mock
    private CurrentUser currentUser;

    /** Answers queued by each test; an exception in the queue is thrown. */
    private final Deque<Object> answers = new ArrayDeque<>();
    private final List<LlmClient.Request> requests = new ArrayList<>();

    private final LlmClient fakeModel = new LlmClient() {
        @Override
        public String provider() {
            return "Fake";
        }

        @Override
        public String model() {
            return "fake-1";
        }

        @Override
        public String complete(Request request) {
            requests.add(request);
            Object next = answers.removeFirst();
            if (next instanceof RuntimeException e) {
                throw e;
            }
            return (String) next;
        }
    };

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.id()).thenReturn(7L);
        lenient().when(catalog.categories()).thenReturn(List.of(PRINTING));
    }

    private AiService service(AiProperties.Mode mode, int perMinute) {
        AiProperties properties = new AiProperties(mode, AiProperties.Provider.ANTHROPIC, "k", null, null,
                Duration.ofSeconds(5), perMinute);
        AiRuntime runtime = new AiRuntime(properties, mode == AiProperties.Mode.LLM ? fakeModel : null);
        LlmJson json = new LlmJson(new ObjectMapper(), Validation.buildDefaultValidatorFactory().getValidator());
        Clock clock = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
        return new AiService(runtime, new AiRateLimiter(properties, clock), json, catalog, similarity, tickets,
                conversation, interventions, currentUser, properties, ZoneId.of("Europe/Paris"));
    }

    private static final TextRequest PRINTER_TICKET = new TextRequest("Imprimante bloquée", "Bourrage papier");

    @Test
    void aValidModelAnswerIsUsedAndLabelled() {
        answers.add("""
                {"categoryId":1,"subcategoryId":10,"impact":"MEDIUM","urgency":"HIGH","confidence":0.92,
                 "reason":"Bourrage d'imprimante"}""");

        ClassificationDto result = service(AiProperties.Mode.LLM, 10).classify(PRINTER_TICKET);

        assertThat(result.source()).isEqualTo(AiSource.LLM);
        assertThat(result.disclaimer()).contains("Fake", "fake-1");
        assertThat(result.category().id()).isEqualTo(1L);
        assertThat(result.subcategory().id()).isEqualTo(10L);
        assertThat(result.priority()).isEqualTo(PriorityCode.HIGH);
        assertThat(result.reasons()).containsExactly("Bourrage d'imprimante");
        assertThat(requests.getFirst().messages().getFirst().content()).contains("id 1 : Impression");
    }

    @Test
    void anUnknownCategoryAnInvalidJsonOrAnErrorFallBackToTheHeuristic() {
        AiService service = service(AiProperties.Mode.LLM, 10);
        answers.add("{\"categoryId\":99,\"subcategoryId\":0,\"impact\":\"LOW\",\"urgency\":\"LOW\","
                + "\"confidence\":0.5,\"reason\":\"x\"}");
        answers.add("Voici ma réponse : la catégorie Impression.");
        answers.add("{\"categoryId\":1,\"subcategoryId\":0,\"impact\":\"LOW\",\"urgency\":\"LOW\","
                + "\"confidence\":7,\"reason\":\"x\"}");
        answers.add(new LlmException("timeout"));

        for (int i = 0; i < 4; i++) {
            ClassificationDto result = service.classify(PRINTER_TICKET);
            assertThat(result.source()).isEqualTo(AiSource.HEURISTIC);
            assertThat(result.disclaimer()).isEqualTo(AiService.FALLBACK_DISCLAIMER);
            assertThat(result.category().name()).isEqualTo("Impression");
        }
    }

    @Test
    void pastThePerUserLimitTheHeuristicAnswersWithoutCallingTheModel() {
        AiService service = service(AiProperties.Mode.LLM, 1);
        answers.add("{\"categoryId\":1,\"subcategoryId\":0,\"impact\":\"LOW\",\"urgency\":\"LOW\","
                + "\"confidence\":0.5,\"reason\":\"x\"}");

        assertThat(service.classify(PRINTER_TICKET).source()).isEqualTo(AiSource.LLM);
        ClassificationDto limited = service.classify(PRINTER_TICKET);

        assertThat(limited.source()).isEqualTo(AiSource.HEURISTIC);
        assertThat(limited.disclaimer()).isEqualTo(AiService.RATE_LIMIT_DISCLAIMER);
        assertThat(requests).hasSize(1);
    }

    @Test
    void heuristicAndDisabledModesNeverCallAModel() {
        ClassificationDto heuristic = service(AiProperties.Mode.HEURISTIC, 10).classify(PRINTER_TICKET);
        assertThat(heuristic.source()).isEqualTo(AiSource.HEURISTIC);
        assertThat(heuristic.disclaimer()).isEqualTo(AiService.HEURISTIC_DISCLAIMER);
        assertThat(heuristic.impact()).isEqualTo(Level.LOW);

        ClassificationDto disabled = service(AiProperties.Mode.DISABLED, 10).classify(PRINTER_TICKET);
        assertThat(disabled.source()).isEqualTo(AiSource.DISABLED);
        assertThat(disabled.category()).isNull();
        assertThat(requests).isEmpty();
    }

    private static RelatedArticle article(long id, String title, double score) {
        return new RelatedArticle(new ArticleSummaryDto(id, title, "Résumé", ArticleStatus.PUBLISHED, null, null,
                0, 0, null, null), "Contenu de " + title, score);
    }

    @Test
    void theAssistantAnswersFromTheRetrievedArticlesAndCitesThemFirst() {
        when(similarity.articlesWithContent(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(article(1, "Wi-Fi", 0.4), article(2, "Poste lent", 0.3)));
        answers.add("{\"answer\":\"Redémarrez le poste.\",\"citedArticleIds\":[2],\"suggestTicket\":false}");

        AssistantAnswerDto answer = service(AiProperties.Mode.LLM, 10).assistant(new AssistantRequest(
                "Mon poste est lent", List.of(new ChatTurn(ChatRole.ASSISTANT, "Bonjour"),
                new ChatTurn(ChatRole.USER, "J'ai un souci"), new ChatTurn(ChatRole.ASSISTANT, "Lequel ?"))));

        assertThat(answer.source()).isEqualTo(AiSource.LLM);
        assertThat(answer.answer()).isEqualTo("Redémarrez le poste.");
        assertThat(answer.articles()).extracting(a -> a.article().id()).containsExactly(2L, 1L);
        List<LlmClient.Message> sent = requests.getFirst().messages();
        assertThat(sent.getFirst().role()).as("the conversation starts with the user").isEqualTo(LlmClient.Role.USER);
        assertThat(sent.getLast().content()).contains("[article id 1]", "[article id 2]", "Mon poste est lent");
    }

    @Test
    void anAnswerCitingAnArticleItWasNotGivenIsRejected() {
        when(similarity.articlesWithContent(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(article(1, "Wi-Fi", 0.4)));
        answers.add("{\"answer\":\"Voir l'article 42.\",\"citedArticleIds\":[42],\"suggestTicket\":false}");

        AssistantAnswerDto answer = service(AiProperties.Mode.LLM, 10).assistant(
                new AssistantRequest("Wi-Fi coupé", null));

        assertThat(answer.source()).isEqualTo(AiSource.HEURISTIC);
        assertThat(answer.answer()).isNull();
        assertThat(answer.articles()).hasSize(1);
    }

    @Test
    void withoutAnyRelevantArticleTheModelIsNotCalledAndATicketIsSuggested() {
        when(similarity.articlesWithContent(anyString(), anyString(), anyInt())).thenReturn(List.of());

        AssistantAnswerDto answer = service(AiProperties.Mode.LLM, 10).assistant(
                new AssistantRequest("Comment commander un badge de parking ?", null));

        assertThat(answer.suggestTicket()).isTrue();
        assertThat(answer.disclaimer()).isEqualTo(AiService.NO_SOURCE_DISCLAIMER);
        assertThat(requests).isEmpty();
    }

    @Test
    void consecutiveTurnsOfTheSameRoleAreMerged() {
        List<LlmClient.Message> merged = AiService.alternate(List.of(
                new LlmClient.Message(LlmClient.Role.ASSISTANT, "a"),
                new LlmClient.Message(LlmClient.Role.USER, "u1"),
                new LlmClient.Message(LlmClient.Role.USER, "u2"),
                new LlmClient.Message(LlmClient.Role.ASSISTANT, "a2")));

        assertThat(merged).extracting(LlmClient.Message::content).containsExactly("u1\n\nu2", "a2");
    }

    @Test
    void relevanceKeepsResultsCloseToTheBestOne() {
        List<Double> kept = SimilarityService.relevant(List.of(0.4, 0.25, 0.15, 0.09), Double::doubleValue);
        assertThat(kept).containsExactly(0.4, 0.25);
        assertThat(SimilarityService.relevant(List.of(0.07), Double::doubleValue)).isEmpty();
        assertThat(SimilarityService.percent(0.2, 0.4)).isEqualTo(50);
    }
}
