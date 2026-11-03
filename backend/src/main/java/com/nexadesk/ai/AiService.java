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
import com.nexadesk.ai.dto.AiDtos.Confidence;
import com.nexadesk.ai.dto.AiDtos.ResponseMode;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    static final String NO_ANSWER_DISCLAIMER = "Aucun article de la base de connaissances ne correspond.";
    /** Opens every fallback answer: written by the server, so it never depends on the model. */
    static final String FALLBACK_INTRO = "Je n'ai pas trouvé de solution correspondante dans la base de "
            + "connaissances de votre entreprise. Voici une suggestion basée sur des connaissances générales en "
            + "informatique, à vérifier avant de l'appliquer :";

    private static final int SIMILAR_LIMIT = 5;
    private static final int SUGGESTION_TICKETS = 5;
    private static final int SUGGESTION_ARTICLES = 3;
    /** Up to this many words, a question that finds nothing alone is read as a follow-up. */
    static final int FOLLOW_UP_MAX_WORDS = 5;

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
    private final AssistantProperties assistantProperties;
    private final ZoneId zone;

    public AiService(AiRuntime runtime, AiRateLimiter rateLimiter, LlmJson llmJson, AiCatalog catalog,
                     SimilarityService similarity, TicketService tickets, TicketConversationService conversation,
                     InterventionService interventions, CurrentUser currentUser, AiProperties properties,
                     AssistantProperties assistantProperties, @Value("${nexadesk.time-zone}") ZoneId zone) {
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
        this.assistantProperties = assistantProperties;
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
     * IT assistant: RAG on the knowledge base, with a fallback on the model's general knowledge.
     * <ol>
     *   <li>Published articles from the candidate score are retrieved (PostgreSQL search).</li>
     *   <li>The model judges which of them really answer the question, and writes the answer from them; when none
     *       does, from its general IT knowledge. One call: the judgement and the answer come together.</li>
     *   <li>The server sets the mode from the articles the model actually used, never from its claims:
     *       {@link ResponseMode#KNOWLEDGE_BASE}, {@link ResponseMode#HYBRID} or {@link ResponseMode#AI_FALLBACK}.
     *   </li>
     * </ol>
     * Without a model (or when it fails), only the articles above the reliable score are listed; there is no
     * generated text and no fallback ({@link ResponseMode#NO_ANSWER} when nothing fits).
     */
    public AssistantAnswerDto assistant(AssistantRequest request) {
        List<ChatTurn> history = request.history() == null ? List.of() : request.history();
        String previous = history.stream().filter(turn -> turn.role() == ChatRole.USER)
                .reduce((first, second) -> second).map(ChatTurn::content).orElse("");
        List<RelatedArticle> candidates = assistantArticles(request.question(), previous);

        AssistantAnswerDto answer;
        if (runtime.mode() == AiSource.DISABLED) {
            answer = localAnswer(candidates).dto(AiSource.DISABLED, DISABLED_DISCLAIMER);
        } else {
            Outcome<AssistantReply> outcome = generate(
                    client -> modelAnswer(client, request.question(), history, candidates),
                    () -> localAnswer(candidates),
                    HEURISTIC_DISCLAIMER);
            AssistantReply reply = outcome.value();
            answer = reply.dto(outcome.source(), outcome.source() == AiSource.LLM
                    ? assistantDisclaimer(reply.mode(), runtime.client().orElseThrow()) : outcome.disclaimer());
        }
        log.info("Assistant answer: mode={}, source={}, candidates={}, sources={}, bestScore={}",
                answer.responseMode(), answer.source(), candidates.size(),
                answer.articles().stream().map(m -> m.article().id()).toList(),
                candidates.isEmpty() ? "-" : String.format(Locale.ROOT, "%.3f", candidates.getFirst().score()));
        return answer;
    }

    /** The answer before the server adds who answered and the matching disclaimer. */
    private record AssistantReply(ResponseMode mode, String text, List<ArticleMatchDto> sources,
                                  Confidence confidence, boolean suggestTicket) {

        AssistantAnswerDto dto(AiSource source, String disclaimer) {
            return new AssistantAnswerDto(source, disclaimer, mode, text, sources, confidence, suggestTicket);
        }
    }

    private AssistantReply modelAnswer(LlmClient client, String question, List<ChatTurn> history,
                                       List<RelatedArticle> candidates) {
        List<LlmClient.Message> messages = new ArrayList<>(history.stream()
                .map(turn -> new LlmClient.Message(turn.role() == ChatRole.USER ? LlmClient.Role.USER
                        : LlmClient.Role.ASSISTANT, turn.content()))
                .toList());
        messages.add(user(AiPrompts.assistantInput(question, candidates)));
        AiPrompts.Answer answer = ask(client, AiPrompts.ASSISTANT_SYSTEM, alternate(messages),
                AiPrompts.ASSISTANT_SCHEMA, 4096, AiPrompts.Answer.class);
        requireKnown(answer.usedArticleIds(), candidates.stream().map(a -> a.article().id()).toList());

        Set<Long> used = new HashSet<>(answer.usedArticleIds());
        List<RelatedArticle> sources = candidates.stream().filter(a -> used.contains(a.article().id())).toList();
        if (sources.isEmpty()) {
            // General knowledge only: the server says so itself, and never rates it as certain.
            Confidence confidence = answer.confidence() == Confidence.HIGH ? Confidence.MEDIUM : answer.confidence();
            return new AssistantReply(ResponseMode.AI_FALLBACK, FALLBACK_INTRO + "\n\n" + answer.answer(),
                    List.of(), confidence, true);
        }
        return new AssistantReply(answer.usesGeneralKnowledge() ? ResponseMode.HYBRID : ResponseMode.KNOWLEDGE_BASE,
                answer.answer(), matches(sources), answer.confidence(), answer.suggestTicket());
    }

    /** Without a model: only the articles a keyword search can be trusted on, as they are. */
    private AssistantReply localAnswer(List<RelatedArticle> candidates) {
        List<RelatedArticle> reliable = candidates.stream()
                .filter(a -> a.score() >= assistantProperties.reliableScore()).toList();
        return reliable.isEmpty()
                ? new AssistantReply(ResponseMode.NO_ANSWER, null, List.of(), null, true)
                : new AssistantReply(ResponseMode.KNOWLEDGE_BASE, null, matches(reliable), null, false);
    }

    private static String assistantDisclaimer(ResponseMode mode, LlmClient client) {
        String model = "un modèle d'IA (" + client.provider() + " – " + client.model() + ")";
        return switch (mode) {
            case KNOWLEDGE_BASE -> "Réponse rédigée par " + model + " à partir de la documentation interne : "
                    + "vérifiez avant d'appliquer.";
            case HYBRID -> "Réponse de " + model + " : la documentation interne, complétée de conseils généraux "
                    + "qui n'ont pas été validés par l'entreprise.";
            case AI_FALLBACK -> "Suggestion de " + model + ", issue de connaissances générales : elle ne provient "
                    + "pas de la documentation interne et n'a pas été validée par l'entreprise.";
            case NO_ANSWER -> NO_ANSWER_DISCLAIMER;
        };
    }

    /**
     * A new question is searched alone, otherwise the previous topic would bring its articles back for an
     * unrelated request. A short follow-up ("et sur Mac ?") that finds nothing alone borrows the previous question.
     */
    private List<RelatedArticle> assistantArticles(String question, String previous) {
        double minScore = assistantProperties.candidateMinScore();
        int limit = assistantProperties.maxArticles();
        List<RelatedArticle> alone = similarity.articleCandidates(question, question, minScore, limit);
        if (!alone.isEmpty() || previous.isBlank() || !isFollowUp(question)) {
            return alone;
        }
        // The title comparison takes the context too: "Et en couleur ?" alone looks like no article title.
        String context = question + " " + previous;
        return similarity.articleCandidates(context, context, minScore, limit);
    }

    static boolean isFollowUp(String question) {
        return question.trim().split("\\s+").length <= FOLLOW_UP_MAX_WORDS;
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
