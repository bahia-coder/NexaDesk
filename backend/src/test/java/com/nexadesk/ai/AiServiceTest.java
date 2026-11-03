package com.nexadesk.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.nexadesk.ai.dto.AiDtos.Confidence;
import com.nexadesk.ai.dto.AiDtos.ResponseMode;
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
                conversation, interventions, currentUser, properties, ASSISTANT, ZoneId.of("Europe/Paris"));
    }

    /** Same thresholds as application.yml. */
    private static final AssistantProperties ASSISTANT = new AssistantProperties(0.08, 0.35, 4);

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

    private void candidates(RelatedArticle... articles) {
        when(similarity.articleCandidates(anyString(), anyString(), eq(0.08), eq(4))).thenReturn(List.of(articles));
    }

    private static String modelAnswer(String answer, String usedIds, boolean general, String confidence,
                                      boolean ticket) {
        return "{\"answer\":\"" + answer + "\",\"usedArticleIds\":[" + usedIds + "],\"usesGeneralKnowledge\":"
                + general + ",\"confidence\":\"" + confidence + "\",\"suggestTicket\":" + ticket + "}";
    }

    @Test
    void knowledgeBase_theModelAnswersFromTheArticlesItJudgedRelevantAndOnlyThoseAreSources() {
        candidates(article(1, "Wi-Fi instable", 0.42), article(2, "Portable ne s'allume plus", 0.34));
        answers.add(modelAnswer("1. Oubliez le réseau puis reconnectez-vous.", "1", false, "HIGH", false));

        AssistantAnswerDto answer = service(AiProperties.Mode.LLM, 10).assistant(new AssistantRequest(
                "Mon ordinateur ne se connecte plus au Wi-Fi", List.of(new ChatTurn(ChatRole.ASSISTANT, "Bonjour"),
                new ChatTurn(ChatRole.USER, "J'ai un souci"), new ChatTurn(ChatRole.ASSISTANT, "Lequel ?"))));

        assertThat(answer.responseMode()).isEqualTo(ResponseMode.KNOWLEDGE_BASE);
        assertThat(answer.source()).isEqualTo(AiSource.LLM);
        assertThat(answer.answer()).isEqualTo("1. Oubliez le réseau puis reconnectez-vous.");
        assertThat(answer.articles()).as("the article sharing words only is not a source")
                .extracting(a -> a.article().id()).containsExactly(1L);
        assertThat(answer.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(answer.suggestTicket()).isFalse();
        assertThat(answer.disclaimer()).contains("documentation interne", "Fake");
        List<LlmClient.Message> sent = requests.getFirst().messages();
        assertThat(sent.getFirst().role()).as("the conversation starts with the user").isEqualTo(LlmClient.Role.USER);
        assertThat(sent.getLast().content()).contains("[article id 1]", "[article id 2]", "ne se connecte plus");
        assertThat(requests.getFirst().system()).contains("N'invente jamais de procédure",
                "Ne propose jamais de désactiver une protection");
    }

    @Test
    void hybrid_articlesCompletedByGeneralAdviceAreFlaggedAsSuch() {
        candidates(article(3, "Erreur VPN 809", 0.64));
        answers.add(modelAnswer("Selon l'article… Conseils généraux (hors documentation interne) : …", "3", true,
                "MEDIUM", false));

        AssistantAnswerDto answer = service(AiProperties.Mode.LLM, 10).assistant(
                new AssistantRequest("Le VPN affiche l'erreur 809 depuis un hôtel", null));

        assertThat(answer.responseMode()).isEqualTo(ResponseMode.HYBRID);
        assertThat(answer.articles()).extracting(a -> a.article().id()).containsExactly(3L);
        assertThat(answer.disclaimer()).contains("conseils généraux", "pas été validés");
    }

    @Test
    void aiFallback_whenNoArticleFitsTheModelAnswersFromGeneralKnowledgeWithoutSources() {
        // A keyword match that does not answer the question: the model sets it aside.
        candidates(article(4, "Ordinateur lent", 0.16));
        answers.add(modelAnswer("Causes possibles : … 1. Fermez Excel puis…", "", true, "HIGH", false));

        AssistantAnswerDto answer = service(AiProperties.Mode.LLM, 10).assistant(
                new AssistantRequest("Excel plante quand j'ouvre un gros fichier", null));

        assertThat(answer.responseMode()).isEqualTo(ResponseMode.AI_FALLBACK);
        assertThat(answer.articles()).as("no made-up source").isEmpty();
        assertThat(answer.answer()).startsWith(AiService.FALLBACK_INTRO).contains("Fermez Excel");
        assertThat(answer.confidence()).as("general knowledge is never rated certain")
                .isEqualTo(Confidence.MEDIUM);
        assertThat(answer.suggestTicket()).isTrue();
        assertThat(answer.disclaimer()).contains("connaissances générales", "pas été validée par l'entreprise");
    }

    @Test
    void aiFallback_theModelIsAskedEvenWhenTheSearchFindsNothing() {
        candidates();
        answers.add(modelAnswer("Vérifiez le mode avion…", "", true, "LOW", true));

        AssistantAnswerDto answer = service(AiProperties.Mode.LLM, 10).assistant(
                new AssistantRequest("Mon ordinateur ne voit plus aucun réseau", null));

        assertThat(answer.responseMode()).isEqualTo(ResponseMode.AI_FALLBACK);
        assertThat(answer.confidence()).isEqualTo(Confidence.LOW);
        assertThat(requests.getFirst().messages().getLast().content()).contains("(aucun)");
    }

    @Test
    void anAnswerCitingAnArticleItWasNotGivenIsRejectedAndTheReliableArticlesAreListed() {
        candidates(article(1, "Wi-Fi instable", 0.42), article(2, "Ordinateur lent", 0.16));
        answers.add(modelAnswer("Voir l'article 42.", "42", false, "HIGH", false));

        AssistantAnswerDto answer = service(AiProperties.Mode.LLM, 10).assistant(
                new AssistantRequest("Wi-Fi coupé", null));

        assertThat(answer.source()).isEqualTo(AiSource.HEURISTIC);
        assertThat(answer.disclaimer()).isEqualTo(AiService.FALLBACK_DISCLAIMER);
        assertThat(answer.responseMode()).isEqualTo(ResponseMode.KNOWLEDGE_BASE);
        assertThat(answer.answer()).isNull();
        assertThat(answer.articles()).extracting(a -> a.article().id()).containsExactly(1L);
    }

    @Test
    void withoutAModelOnlyReliableArticlesAreListedAndThereIsNoFallback() {
        AiService service = service(AiProperties.Mode.HEURISTIC, 10);
        candidates(article(3, "Erreur VPN 809", 0.64), article(4, "Ordinateur lent", 0.16));
        AssistantAnswerDto found = service.assistant(new AssistantRequest("Erreur VPN 809", null));
        assertThat(found.responseMode()).isEqualTo(ResponseMode.KNOWLEDGE_BASE);
        assertThat(found.articles()).extracting(a -> a.article().id()).containsExactly(3L);
        assertThat(found.confidence()).isNull();

        candidates(article(4, "Ordinateur lent", 0.16));
        AssistantAnswerDto none = service.assistant(new AssistantRequest("Excel plante", null));
        assertThat(none.responseMode()).isEqualTo(ResponseMode.NO_ANSWER);
        assertThat(none.answer()).isNull();
        assertThat(none.articles()).isEmpty();
        assertThat(none.suggestTicket()).isTrue();
        assertThat(none.disclaimer()).isEqualTo(AiService.HEURISTIC_DISCLAIMER);
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
