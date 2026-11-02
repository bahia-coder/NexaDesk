package com.nexadesk.ai;

import com.nexadesk.ai.HeuristicClassifier.CategoryOption;
import com.nexadesk.ai.HeuristicClassifier.SubOption;
import com.nexadesk.ai.dto.AiDtos.AiStatusDto;
import com.nexadesk.ai.dto.AiDtos.ArticleMatchDto;
import com.nexadesk.ai.dto.AiDtos.AssistantAnswerDto;
import com.nexadesk.ai.dto.AiDtos.AssistantRequest;
import com.nexadesk.ai.dto.AiDtos.ChatRole;
import com.nexadesk.ai.dto.AiDtos.ChatTurn;
import com.nexadesk.ai.dto.AiDtos.ClassificationDto;
import com.nexadesk.ai.dto.AiDtos.SimilarDto;
import com.nexadesk.ai.dto.AiDtos.SimilarTicketDto;
import com.nexadesk.ai.dto.AiDtos.SuggestionsDto;
import com.nexadesk.ai.dto.AiDtos.SummaryDto;
import com.nexadesk.ai.dto.AiDtos.TextRequest;
import com.nexadesk.ai.llm.LlmClient;
import com.nexadesk.ai.llm.LlmException;
import com.nexadesk.category.PriorityMatrix;
import com.nexadesk.category.dto.CategoryDtos.CategoryRefDto;
import com.nexadesk.intervention.InterventionService;
import com.nexadesk.intervention.dto.InterventionDtos.TicketInterventionsDto;
import com.nexadesk.knowledge.KnowledgeService.RelatedArticle;
import com.nexadesk.security.CurrentUser;
import com.nexadesk.ticket.TicketConversationService;
import com.nexadesk.ticket.TicketService;
import com.nexadesk.ticket.dto.TicketDtos.SubCategoryRefDto;
import com.nexadesk.ticket.dto.TicketDtos.TicketDetailDto;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI features. Each one has a model path and a local path; the model is only used when connected, within the
 * per-user rate limit, and its answer is validated. Any failure (timeout, quota, invalid JSON, unknown id) falls
 * back to the local path, and the response says which one answered ({@link AiSource} + disclaimer).
 *
 * <p>No database transaction is held during a model call: data is read first, through the regular services
 * (so their access rules apply), then the model is called.
 */
@Slf4j
@Service
public class AiService {

    static final String HEURISTIC_DISCLAIMER = "Mode démonstration — heuristique locale, sans modèle d'IA.";
    static final String SUMMARY_DISCLAIMER = "Résumé automatique (non IA) : faits extraits du ticket.";
    static final String DISABLED_DISCLAIMER = "Les fonctions d'IA sont désactivées sur cette installation.";
    static final String FALLBACK_DISCLAIMER = "Le modèle d'IA n'a pas pu répondre : résultat de l'heuristique "
            + "locale, sans modèle d'IA.";
    static final String RATE_LIMIT_DISCLAIMER = "Limite de requêtes IA atteinte pour cette minute : résultat de "
            + "l'heuristique locale, sans modèle d'IA.";
    static final String NO_SOURCE_DISCLAIMER = "Aucun article pertinent trouvé : le modèle d'IA n'a pas été "
            + "sollicité.";

    private static final int SIMILAR_LIMIT = 5;
    private static final int SUGGESTION_TICKETS = 5;
    private static final int SUGGESTION_ARTICLES = 3;
    private static final int ASSISTANT_ARTICLES = 4;

    private final AiRuntime runtime;
    private final AiRateLimiter rateLimiter;
    private final LlmJson llmJson;
    private final AiCatalog catalog;
    private final SimilarityService similarity;
    private final TicketService tickets;
    private final TicketConversationService conversation;
    private final InterventionService interventions;
    private final CurrentUser currentUser;
    private final AiProperties properties;
    private final ZoneId zone;

    public AiService(AiRuntime runtime, AiRateLimiter rateLimiter, LlmJson llmJson, AiCatalog catalog,
                     SimilarityService similarity, TicketService tickets, TicketConversationService conversation,
                     InterventionService interventions, CurrentUser currentUser, AiProperties properties,
                     @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.runtime = runtime;
        this.rateLimiter = rateLimiter;
        this.llmJson = llmJson;
        this.catalog = catalog;
        this.similarity = similarity;
        this.tickets = tickets;
        this.conversation = conversation;
        this.interventions = interventions;
        this.currentUser = currentUser;
        this.properties = properties;
        this.zone = zone;
    }

    /** What answered, and the disclaimer to show with it. */
    private record Outcome<T>(T value, AiSource source, String disclaimer) {
    }

    public AiStatusDto status() {
        Optional<LlmClient> client = runtime.client();
        return new AiStatusDto(runtime.mode(), client.map(LlmClient::provider).orElse(null),
                client.map(LlmClient::model).orElse(null), disclaimer(runtime.mode(), HEURISTIC_DISCLAIMER),
                properties.requestsPerMinute());
    }

    // ---- Classification ----------------------------------------------------------------------------------

    public ClassificationDto classify(TextRequest request) {
        if (runtime.mode() == AiSource.DISABLED) {
            return new ClassificationDto(AiSource.DISABLED, DISABLED_DISCLAIMER, null, null, null, null, null, 0,
                    List.of());
        }
        List<CategoryOption> categories = catalog.categories();
        Outcome<ClassificationDto> outcome = generate(
                client -> classifyWithModel(client, request, categories),
                () -> classifyLocally(request, categories),
                HEURISTIC_DISCLAIMER);
        ClassificationDto value = outcome.value();
        return new ClassificationDto(outcome.source(), outcome.disclaimer(), value.category(), value.subcategory(),
                value.impact(), value.urgency(), value.priority(), value.confidence(), value.reasons());
    }

    private ClassificationDto classifyWithModel(LlmClient client, TextRequest request,
                                                List<CategoryOption> categories) {
        AiPrompts.Classification answer = ask(client, AiPrompts.CLASSIFY_SYSTEM,
                List.of(user(AiPrompts.classifyInput(request.title(), request.description(), categories))),
                AiPrompts.CLASSIFY_SCHEMA, 2048, AiPrompts.Classification.class);
        CategoryOption category = categories.stream().filter(c -> c.id().equals(answer.categoryId())).findFirst()
                .orElseThrow(() -> new LlmException("Catégorie inconnue proposée : " + answer.categoryId()));
        SubOption sub = AiPrompts.subcategory(category, answer.subcategoryId());
        return new ClassificationDto(null, null, ref(category), ref(sub), answer.impact(), answer.urgency(),
                PriorityMatrix.priorityFor(answer.impact(), answer.urgency()),
                Math.round(answer.confidence() * 100) / 100.0, List.of(answer.reason()));
    }

    private static ClassificationDto classifyLocally(TextRequest request, List<CategoryOption> categories) {
        HeuristicClassifier.Result result = HeuristicClassifier.classify(request.title(), request.description(),
                categories);
        return new ClassificationDto(null, null, ref(result.category()), ref(result.subcategory()), result.impact(),
                result.urgency(), PriorityMatrix.priorityFor(result.impact(), result.urgency()),
                result.confidence(), result.reasons());
    }

    // ---- Summary -----------------------------------------------------------------------------------------

    public SummaryDto summary(Long ticketId) {
        TicketContext context = load(ticketId);
        if (runtime.mode() == AiSource.DISABLED) {
            return new SummaryDto(AiSource.DISABLED, DISABLED_DISCLAIMER, null, List.of());
        }
        Outcome<HeuristicSummary.Summary> outcome = generate(
                client -> {
                    AiPrompts.Summary answer = ask(client, AiPrompts.SUMMARY_SYSTEM,
                            List.of(user(context.forModel(zone))), AiPrompts.SUMMARY_SCHEMA, 4096,
                            AiPrompts.Summary.class);
                    return new HeuristicSummary.Summary(answer.summary(), answer.keyPoints());
                },
                () -> HeuristicSummary.summarize(context, zone),
                SUMMARY_DISCLAIMER);
        return new SummaryDto(outcome.source(), outcome.disclaimer(), outcome.value().text(),
                outcome.value().keyPoints());
    }

    // ---- Similar content (PostgreSQL, always on) ---------------------------------------------------------

    public SimilarDto similarToTicket(Long ticketId, int limit) {
        TicketDetailDto ticket = tickets.get(ticketId);
        int max = Math.clamp(limit, 1, 20);
        String text = ticket.title() + " " + ticket.description();
        return new SimilarDto(similarity.tickets(ticket.title(), text, ticketId, false, max),
                similarity.articles(ticket.title(), text, max));
    }

    /** Before creation: "this problem may already have a solution". */
    public SimilarDto similarToText(TextRequest request) {
        String text = request.title() + " " + (request.description() == null ? "" : request.description());
        return new SimilarDto(similarity.tickets(request.title(), text, null, false, SIMILAR_LIMIT),
                similarity.articles(request.title(), text, SIMILAR_LIMIT));
    }

    // ---- Resolution suggestions --------------------------------------------------------------------------

    public SuggestionsDto suggestions(Long ticketId) {
        TicketDetailDto ticket = tickets.get(ticketId);
        String text = ticket.title() + " " + ticket.description();
        List<SimilarTicketDto> solved = similarity.tickets(ticket.title(), text, ticketId, true, SUGGESTION_TICKETS);
        List<RelatedArticle> articles = similarity.articlesWithContent(ticket.title(), text, SUGGESTION_ARTICLES);
        List<ArticleMatchDto> articleMatches = matches(articles);
        if (runtime.mode() == AiSource.DISABLED) {
            return new SuggestionsDto(AiSource.DISABLED, DISABLED_DISCLAIMER, List.of(), solved, articleMatches);
        }
        if (solved.isEmpty() && articles.isEmpty()) {
            return new SuggestionsDto(AiSource.HEURISTIC, runtime.mode() == AiSource.LLM ? NO_SOURCE_DISCLAIMER
                    : HEURISTIC_DISCLAIMER, List.of(), solved, articleMatches);
        }
        Outcome<List<String>> outcome = generate(
                client -> {
                    AiPrompts.Suggestions answer = ask(client, AiPrompts.SUGGEST_SYSTEM,
                            List.of(user(AiPrompts.suggestInput(ticket.title(), ticket.description(), solved,
                                    articles))),
                            AiPrompts.SUGGEST_SCHEMA, 4096, AiPrompts.Suggestions.class);
                    requireKnown(answer.usedTicketIds(), solved.stream().map(s -> s.ticket().id()).toList());
                    requireKnown(answer.usedArticleIds(), articles.stream().map(a -> a.article().id()).toList());
                    return answer.steps();
                },
                List::of,
                HEURISTIC_DISCLAIMER);
        return new SuggestionsDto(outcome.source(), outcome.disclaimer(), outcome.value(), solved, articleMatches);
    }

    // ---- Assistant ---------------------------------------------------------------------------------------

    /**
     * Knowledge-base assistant (simple RAG): the most relevant published articles are retrieved with PostgreSQL,
     * then the model answers from them only. Without a model, the articles themselves are the answer.
     */
    public AssistantAnswerDto assistant(AssistantRequest request) {
        List<ChatTurn> history = request.history() == null ? List.of() : request.history();
        // The previous question gives context to a follow-up such as "et sur Mac ?".
        String previous = history.stream().filter(turn -> turn.role() == ChatRole.USER)
                .reduce((first, second) -> second).map(ChatTurn::content).orElse("");
        List<RelatedArticle> articles = similarity.articlesWithContent(request.question(),
                request.question() + " " + previous, ASSISTANT_ARTICLES);
        List<ArticleMatchDto> matches = matches(articles);
        if (runtime.mode() == AiSource.DISABLED) {
            return new AssistantAnswerDto(AiSource.DISABLED, DISABLED_DISCLAIMER, null, matches, matches.isEmpty());
        }
        if (articles.isEmpty()) {
            return new AssistantAnswerDto(AiSource.HEURISTIC, runtime.mode() == AiSource.LLM ? NO_SOURCE_DISCLAIMER
                    : HEURISTIC_DISCLAIMER, null, matches, true);
        }
        Outcome<AiPrompts.Answer> outcome = generate(
                client -> {
                    List<LlmClient.Message> messages = new ArrayList<>(history.stream()
                            .map(turn -> new LlmClient.Message(turn.role() == ChatRole.USER ? LlmClient.Role.USER
                                    : LlmClient.Role.ASSISTANT, turn.content()))
                            .toList());
                    messages.add(user(AiPrompts.assistantInput(request.question(), articles)));
                    AiPrompts.Answer answer = ask(client, AiPrompts.ASSISTANT_SYSTEM, alternate(messages),
                            AiPrompts.ASSISTANT_SCHEMA, 4096, AiPrompts.Answer.class);
                    requireKnown(answer.citedArticleIds(), articles.stream().map(a -> a.article().id()).toList());
                    return answer;
                },
                () -> null,
                HEURISTIC_DISCLAIMER);
        AiPrompts.Answer answer = outcome.value();
        if (answer == null) {
            return new AssistantAnswerDto(outcome.source(), outcome.disclaimer(), null, matches, false);
        }
        Set<Long> cited = new HashSet<>(answer.citedArticleIds());
        List<ArticleMatchDto> ordered = matches.stream()
                .sorted(Comparator.comparing((ArticleMatchDto m) -> !cited.contains(m.article().id())))
                .toList();
        return new AssistantAnswerDto(outcome.source(), outcome.disclaimer(), answer.answer(), ordered,
                answer.suggestTicket());
    }

    // ---- Model plumbing ----------------------------------------------------------------------------------

    /**
     * Runs the model path when possible, otherwise (or on failure) the local path. {@code localDisclaimer} is
     * shown when the local path answers by design (heuristic mode).
     */
    private <T> Outcome<T> generate(Function<LlmClient, T> model, Supplier<T> local, String localDisclaimer) {
        Optional<LlmClient> client = runtime.client();
        if (client.isEmpty()) {
            return new Outcome<>(local.get(), AiSource.HEURISTIC, localDisclaimer);
        }
        if (!rateLimiter.tryAcquire(currentUser.id())) {
            return new Outcome<>(local.get(), AiSource.HEURISTIC, RATE_LIMIT_DISCLAIMER);
        }
        try {
            return new Outcome<>(model.apply(client.get()), AiSource.LLM,
                    "Généré par un modèle d'IA (" + client.get().provider() + " – " + client.get().model()
                            + ") : vérifiez avant d'appliquer.");
        } catch (RuntimeException e) {
            log.warn("AI model call failed, falling back to the heuristic: {}", e.getMessage());
            return new Outcome<>(local.get(), AiSource.HEURISTIC, FALLBACK_DISCLAIMER);
        }
    }

    private <T> T ask(LlmClient client, String system, List<LlmClient.Message> messages,
                      Map<String, Object> schema, int maxTokens, Class<T> answerType) {
        String raw = client.complete(new LlmClient.Request(system, messages, schema, maxTokens));
        return llmJson.read(raw, answerType);
    }

    /** The model may only refer to sources it was given: an unknown id means a made-up reference. */
    private static void requireKnown(List<Long> used, List<Long> provided) {
        if (!provided.containsAll(used)) {
            throw new LlmException("Le modèle cite une source qui ne lui a pas été fournie.");
        }
    }

    /**
     * Chat APIs expect user and assistant turns to alternate: consecutive turns of the same role (possible in a
     * client-sent history) are merged, and the conversation must start with the user.
     */
    static List<LlmClient.Message> alternate(List<LlmClient.Message> messages) {
        List<LlmClient.Message> result = new ArrayList<>();
        for (LlmClient.Message message : messages) {
            if (result.isEmpty() && message.role() == LlmClient.Role.ASSISTANT) {
                continue;
            }
            if (!result.isEmpty() && result.getLast().role() == message.role()) {
                LlmClient.Message last = result.removeLast();
                result.add(new LlmClient.Message(last.role(), last.content() + "\n\n" + message.content()));
            } else {
                result.add(message);
            }
        }
        return result;
    }

    private TicketContext load(Long ticketId) {
        TicketDetailDto ticket = tickets.get(ticketId);
        TicketInterventionsDto work = interventions.list(ticketId);
        return new TicketContext(ticket, conversation.comments(ticketId), conversation.history(ticketId),
                work.interventions(), work.totalMinutes());
    }

    private static List<ArticleMatchDto> matches(List<RelatedArticle> articles) {
        return SimilarityService.matches(articles);
    }

    private static LlmClient.Message user(String content) {
        return new LlmClient.Message(LlmClient.Role.USER, content);
    }

    private static String disclaimer(AiSource mode, String heuristic) {
        return switch (mode) {
            case LLM -> "Un modèle d'IA est connecté : ses réponses sont signalées et doivent être vérifiées.";
            case HEURISTIC -> heuristic;
            case DISABLED -> DISABLED_DISCLAIMER;
        };
    }

    private static CategoryRefDto ref(CategoryOption category) {
        return category == null ? null : new CategoryRefDto(category.id(), category.name(), category.icon());
    }

    private static SubCategoryRefDto ref(SubOption sub) {
        return sub == null ? null : new SubCategoryRefDto(sub.id(), sub.name());
    }
}
