package com.nexadesk.demo;

import com.nexadesk.category.PriorityCode;
import com.nexadesk.category.PriorityMatrix;
import com.nexadesk.category.PriorityRepository;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.category.TicketSubCategory;
import com.nexadesk.demo.DemoTicketTemplates.Template;
import com.nexadesk.intervention.Intervention;
import com.nexadesk.intervention.InterventionRepository;
import com.nexadesk.sla.BusinessTime;
import com.nexadesk.sla.SlaProperties;
import com.nexadesk.sla.SlaSettings;
import com.nexadesk.sla.WorkingCalendar;
import com.nexadesk.ticket.PendingReason;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketReferenceGenerator;
import com.nexadesk.ticket.TicketRepository;
import com.nexadesk.ticket.TicketStatus;
import com.nexadesk.ticket.comment.TicketComment;
import com.nexadesk.ticket.comment.TicketCommentRepository;
import com.nexadesk.ticket.feedback.TicketFeedback;
import com.nexadesk.ticket.feedback.TicketFeedbackRepository;
import com.nexadesk.ticket.history.HistoryAction;
import com.nexadesk.ticket.history.TicketHistory;
import com.nexadesk.ticket.history.TicketHistoryRepository;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo profile only: 48 tickets over the last six months, with their whole story (assignment, comments,
 * interventions, history, feedback) and SLA deadlines computed with the same {@link BusinessTime} rules as the
 * application. Dates are relative to the start-up date, so the dashboards always show current-looking data,
 * including overdue, at-risk and waiting tickets.
 *
 * <p>Deterministic (fixed random seed) and idempotent (skipped if the first scenario already exists).
 */
@Slf4j
@Component
@Profile("demo")
@Order(4)
public class DemoTicketSeeder implements ApplicationRunner {

    private enum Outcome { CLOSED, AUTO_CLOSED, REJECTED, NEW, ASSIGNED, IN_PROGRESS, PENDING_USER, PENDING_VENDOR, RESOLVED }

    private record Plan(int template, Instant createdAt, Outcome outcome, boolean forSara, Duration resolvedAgo) {
    }

    private static final int OLDER_TICKETS = 34;

    private static final List<String> REJECTION_REASONS = List.of(
            "Doublon d'un ticket déjà ouvert pour le même problème.",
            "Hors périmètre du support IT : à adresser aux services généraux.",
            "Demande déjà traitée lors d'une intervention précédente.");

    private static final List<String> FEEDBACK_COMMENTS = List.of(
            "Rapide et efficace, merci !",
            "Très bonne explication du problème.",
            "Résolu, mais un peu long.",
            "Parfait, merci pour la réactivité.",
            "Le technicien a pris le temps de m'expliquer.");

    private static final int[] RATINGS = {5, 5, 5, 4, 4, 4, 3, 2};

    private final TicketRepository ticketRepository;
    private final TicketCategoryRepository categoryRepository;
    private final PriorityRepository priorityRepository;
    private final UserRepository userRepository;
    private final TicketCommentRepository commentRepository;
    private final TicketHistoryRepository historyRepository;
    private final TicketFeedbackRepository feedbackRepository;
    private final InterventionRepository interventionRepository;
    private final TicketReferenceGenerator referenceGenerator;
    private final SlaSettings slaSettings;
    private final SlaProperties slaProperties;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ZoneId zone;

    // Per-run state
    private final Random random = new Random(2026);
    private Instant now;
    private Instant latest;
    private SlaSettings.Snapshot sla;
    private Map<String, TicketCategory> categories;
    private Map<String, List<User>> specialists;
    private List<User> employees;
    private User sara;
    private User admin;

    public DemoTicketSeeder(TicketRepository ticketRepository, TicketCategoryRepository categoryRepository,
                            PriorityRepository priorityRepository, UserRepository userRepository,
                            TicketCommentRepository commentRepository, TicketHistoryRepository historyRepository,
                            TicketFeedbackRepository feedbackRepository, InterventionRepository interventionRepository,
                            TicketReferenceGenerator referenceGenerator, SlaSettings slaSettings,
                            SlaProperties slaProperties, JdbcClient jdbc, Clock clock,
                            @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.ticketRepository = ticketRepository;
        this.categoryRepository = categoryRepository;
        this.priorityRepository = priorityRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
        this.feedbackRepository = feedbackRepository;
        this.interventionRepository = interventionRepository;
        this.referenceGenerator = referenceGenerator;
        this.slaSettings = slaSettings;
        this.slaProperties = slaProperties;
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (ticketRepository.existsByTitle(DemoTicketTemplates.ALL.getFirst().title())) {
            log.info("Demo tickets already present, skipping");
            return;
        }
        now = clock.instant();
        latest = now.minus(Duration.ofMinutes(5));
        sla = slaSettings.current();
        loadReferenceData();

        List<Plan> plans = new ArrayList<>(olderPlans());
        plans.addAll(recentPlans());
        plans.sort(Comparator.comparing(Plan::createdAt));
        plans.forEach(this::create);
        log.info("Demo tickets created: {}", plans.size());
    }

    // ---- Planning ----------------------------------------------------------------------------------------

    /** Six months of history, one ticket every five days, mostly closed. */
    private List<Plan> olderPlans() {
        WorkingCalendar calendar = sla.calendar();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        List<Plan> plans = new ArrayList<>();
        for (int i = 0; i < OLDER_TICKETS; i++) {
            LocalDate day = today.minusDays(178 - i * 5L);
            while (!calendar.isWorkingDay(day)) {
                day = day.minusDays(1);
            }
            LocalTime time = LocalTime.of(8, 30).plusMinutes(random.nextInt(8 * 60));
            Outcome outcome = i == 5 || i == 17 || i == 29 ? Outcome.REJECTED
                    : i % 6 == 2 && i < 25 ? Outcome.AUTO_CLOSED : Outcome.CLOSED;
            plans.add(new Plan((i * 7) % DemoTicketTemplates.ALL.size(), ZonedDateTime.of(day, time, zone).toInstant(),
                    outcome, i % 4 == 0, null));
        }
        return plans;
    }

    /**
     * The current queue: every status, with overdue, at-risk and waiting tickets. Ages are SLA-clock time (business
     * hours, or calendar time for 24/7 rules), so these states hold whatever day the demo is started — even on a
     * Sunday, when calendar ages would all fall outside working hours.
     */
    private List<Plan> recentPlans() {
        return List.of(
                recent(12, Outcome.NEW, Duration.ofMinutes(90), true, null),         // MEDIUM, on track
                recent(2, Outcome.NEW, Duration.ofMinutes(25), false, null),         // CRITICAL, first response late
                recent(5, Outcome.NEW, Duration.ofHours(30), false, null),           // LOW, on track
                recent(16, Outcome.NEW, Duration.ofHours(26), false, null),          // MEDIUM, overdue, unassigned
                recent(4, Outcome.ASSIGNED, Duration.ofHours(5), false, null),       // LOW
                recent(10, Outcome.ASSIGNED, Duration.ofHours(10), false, null),     // HIGH, overdue
                recent(0, Outcome.IN_PROGRESS, Duration.ofHours(3), true, null),     // HIGH, on track
                recent(22, Outcome.IN_PROGRESS, Duration.ofMinutes(100), false, null), // CRITICAL, at risk
                recent(14, Outcome.IN_PROGRESS, Duration.ofHours(12), false, null),  // HIGH, overdue
                recent(6, Outcome.IN_PROGRESS, Duration.ofHours(19), false, null),   // MEDIUM, at risk (79 %)
                recent(19, Outcome.PENDING_USER, Duration.ofHours(6), true, null),   // waiting for Sara
                recent(11, Outcome.PENDING_VENDOR, Duration.ofHours(20), false, null),
                recent(3, Outcome.RESOLVED, Duration.ofHours(14), true, Duration.ofHours(4)),  // Sara to confirm
                recent(17, Outcome.RESOLVED, Duration.ofHours(6), false, Duration.ofHours(1)));
    }

    private Plan recent(int template, Outcome outcome, Duration clockAge, boolean forSara, Duration resolvedAgo) {
        Template t = DemoTicketTemplates.ALL.get(template);
        boolean businessHours = sla.rule(PriorityMatrix.priorityFor(t.impact(), t.urgency())).businessHoursOnly();
        return new Plan(template, BusinessTime.subtract(now, clockAge, sla.calendar(), businessHours), outcome,
                forSara, resolvedAgo);
    }

    // ---- Story of one ticket -----------------------------------------------------------------------------

    private void create(Plan plan) {
        Template t = DemoTicketTemplates.ALL.get(plan.template());
        TicketCategory category = categories.get(t.category());
        TicketSubCategory subcategory = category.getSubcategories().stream()
                .filter(s -> s.getName().equals(t.subcategory())).findFirst().orElseThrow();
        PriorityCode priorityCode = PriorityMatrix.priorityFor(t.impact(), t.urgency());
        SlaSettings.Rule rule = sla.rule(priorityCode);
        Instant created = plan.createdAt();
        User requester = plan.forSara() ? sara : employees.get(random.nextInt(employees.size()));
        User technician = pick(specialists.get(category.getName()));

        Ticket ticket = Ticket.builder()
                .createdAt(created)
                .reference(referenceGenerator.next(t.type(), created.atZone(zone).getYear()))
                .type(t.type())
                .title(t.title())
                .description(t.description())
                .impact(t.impact())
                .urgency(t.urgency())
                .priority(priorityRepository.findByCode(priorityCode).orElseThrow())
                .category(category)
                .subcategory(subcategory)
                .requester(requester)
                .build();
        ticket.setSlaBusinessHours(rule.businessHoursOnly());
        ticket.setFirstResponseDueAt(add(created, rule.firstResponseMinutes(), rule));
        ticket.setResolutionDueAt(add(created, rule.resolutionMinutes(), rule));
        ticketRepository.save(ticket);

        Story story = new Story(ticket, created);
        story.history(created, requester, HistoryAction.CREATED, "reference", null, ticket.getReference(), null, false);

        switch (plan.outcome()) {
            case NEW -> { }
            case REJECTED -> {
                Instant at = created.plus(Duration.ofMinutes(45));
                ticket.setRejectionReason(REJECTION_REASONS.get(random.nextInt(REJECTION_REASONS.size())));
                ticket.setSlaPausedAt(at);
                story.status(at, admin, TicketStatus.REJECTED, ticket.getRejectionReason());
            }
            default -> work(story, plan, t, rule, technician);
        }
        story.finish();
    }

    /** Assignment, start, comments and interventions, then the outcome of the plan. */
    private void work(Story story, Plan plan, Template t, SlaSettings.Rule rule, User technician) {
        Ticket ticket = story.ticket;
        Instant created = story.created;

        Instant assignedAt = cap(created.plus(Duration.ofMinutes(5 + random.nextInt(35))));
        ticket.setAssignee(technician);
        story.status(assignedAt, technician, TicketStatus.ASSIGNED, "Prise en charge");
        story.history(assignedAt, technician, HistoryAction.ASSIGNED, "assignee", null, technician.getFullName(),
                null, false);
        if (plan.outcome() == Outcome.ASSIGNED) {
            return;
        }

        // First response: usually on time, sometimes late (factor up to 1.4 of the allowed time).
        double responseFactor = 0.3 + random.nextDouble() * 1.1;
        Instant startedAt = cap(later(assignedAt.plus(Duration.ofMinutes(5)),
                add(created, Math.round(rule.firstResponseMinutes() * responseFactor), rule)));
        ticket.setFirstRespondedAt(startedAt);
        story.status(startedAt, technician, TicketStatus.IN_PROGRESS, null);
        story.comment(cap(startedAt.plus(Duration.ofMinutes(2))), technician,
                "Bonjour, je prends en charge votre demande et reviens vers vous rapidement.", false);
        Instant firstWork = cap(startedAt.plus(Duration.ofMinutes(30)));
        story.intervention(firstWork, technician, t.diagnosis(), null, 20 + random.nextInt(40));

        switch (plan.outcome()) {
            case IN_PROGRESS -> story.comment(cap(firstWork.plus(Duration.ofMinutes(5))), technician,
                    "Piste principale : " + t.diagnosis(), true);
            case PENDING_USER, PENDING_VENDOR -> {
                boolean user = plan.outcome() == Outcome.PENDING_USER;
                Instant pausedAt = cap(firstWork.plus(Duration.ofMinutes(10)));
                ticket.setPendingReason(user ? PendingReason.WAITING_USER : PendingReason.WAITING_VENDOR);
                ticket.setSlaPausedAt(pausedAt);
                if (user) {
                    story.comment(pausedAt, technician, "Pouvez-vous me confirmer si le problème se produit aussi "
                            + "sur un autre poste ? Je mets le ticket en attente de votre retour.", false);
                } else {
                    story.comment(pausedAt, technician, "Pièce commandée auprès du fournisseur, livraison sous 3 jours.",
                            true);
                }
                story.status(pausedAt, technician, TicketStatus.PENDING, ticket.getPendingReason().name());
            }
            default -> resolve(story, plan, t, rule, technician, startedAt);
        }
    }

    private void resolve(Story story, Plan plan, Template t, SlaSettings.Rule rule, User technician,
                         Instant startedAt) {
        Ticket ticket = story.ticket;
        Instant resolvedAt = plan.resolvedAgo() != null
                ? BusinessTime.subtract(now, plan.resolvedAgo(), sla.calendar(), true)
                // About one ticket in five misses its deadline (factor above 1).
                : add(story.created, Math.round(rule.resolutionMinutes() * (0.25 + random.nextDouble() * 1.0)), rule);
        resolvedAt = cap(later(startedAt.plus(Duration.ofMinutes(45)), resolvedAt));

        Instant followUp = startedAt.plus(Duration.ofMinutes(15));
        if (followUp.isBefore(resolvedAt)) {
            story.comment(followUp, ticket.getRequester(), t.followUp(), false);
        }
        story.intervention(resolvedAt.minus(Duration.ofMinutes(10)), technician, t.diagnosis(), t.solution(),
                15 + random.nextInt(75));
        story.comment(resolvedAt, technician, "Le problème est résolu : " + t.solution()
                + " Pouvez-vous confirmer que tout fonctionne ?", false);
        ticket.setResolution(t.solution());
        ticket.setResolvedAt(resolvedAt);
        ticket.setSlaPausedAt(resolvedAt);
        story.status(resolvedAt, technician, TicketStatus.RESOLVED, null);

        switch (plan.outcome()) {
            case CLOSED -> {
                Instant closedAt = cap(resolvedAt.plus(Duration.ofHours(2 + random.nextInt(28))));
                ticket.setClosedAt(closedAt);
                story.status(closedAt, ticket.getRequester(), TicketStatus.CLOSED,
                        "Résolution confirmée par le demandeur");
                if (random.nextInt(10) < 8) {
                    story.feedback(cap(closedAt.plus(Duration.ofMinutes(3))));
                }
            }
            case AUTO_CLOSED -> {
                Instant closedAt = cap(BusinessTime.add(resolvedAt, slaProperties.autoCloseAfter(), sla.calendar(),
                        true));
                ticket.setClosedAt(closedAt);
                story.status(closedAt, null, TicketStatus.CLOSED,
                        "Clôture automatique : résolution sans réponse du demandeur");
            }
            default -> { }
        }
    }

    /** Accumulates the timeline of one ticket and writes it with its real dates. */
    private final class Story {

        private final Ticket ticket;
        private final Instant created;
        private Instant lastEvent;
        private final List<Intervention> interventions = new ArrayList<>();
        private final Map<Intervention, Instant> interventionDates = new HashMap<>();

        Story(Ticket ticket, Instant created) {
            this.ticket = ticket;
            this.created = created;
            this.lastEvent = created;
        }

        void status(Instant at, User actor, TicketStatus target, String comment) {
            TicketStatus previous = ticket.getStatus();
            ticket.setStatus(target);
            history(at, actor, HistoryAction.STATUS_CHANGED, "status", previous.name(), target.name(), comment, false);
        }

        void history(Instant at, User actor, HistoryAction action, String field, String oldValue, String newValue,
                     String comment, boolean internal) {
            historyRepository.save(TicketHistory.builder().ticket(ticket).actor(actor).action(action).field(field)
                    .oldValue(oldValue).newValue(newValue).comment(comment).internal(internal).createdAt(at).build());
            touch(at);
        }

        void comment(Instant at, User author, String body, boolean internal) {
            commentRepository.save(new TicketComment(ticket, author, body, internal, at));
            if (internal) {
                history(at, author, HistoryAction.COMMENT_ADDED, null, null, null, "Note interne", true);
            } else {
                history(at, author, HistoryAction.COMMENT_ADDED, null, null, null, null, false);
            }
        }

        void intervention(Instant at, User technician, String diagnosis, String solution, int minutes) {
            Intervention intervention = new Intervention(ticket, technician);
            intervention.setDiagnosis(diagnosis);
            intervention.setSolution(solution);
            intervention.setTimeSpentMinutes(minutes);
            intervention.setEndedAt(at);
            intervention.setStartedAt(at.minus(Duration.ofMinutes(minutes)));
            interventionRepository.save(intervention);
            interventions.add(intervention);
            interventionDates.put(intervention, at);
            history(at, technician, HistoryAction.INTERVENTION_ADDED, null, null, null,
                    "Intervention : " + minutes + " min", true);
        }

        void feedback(Instant at) {
            int rating = RATINGS[random.nextInt(RATINGS.length)];
            String comment = random.nextBoolean() ? FEEDBACK_COMMENTS.get(random.nextInt(FEEDBACK_COMMENTS.size())) : null;
            feedbackRepository.save(new TicketFeedback(ticket, ticket.getRequester(), (short) rating, comment, at));
            history(at, ticket.getRequester(), HistoryAction.FEEDBACK_ADDED, "rating", null, rating + "/5", comment,
                    false);
        }

        /** Writes the ticket and interventions, then restores their real creation dates (set by Hibernate on insert). */
        void finish() {
            ticketRepository.saveAndFlush(ticket);
            jdbc.sql("update tickets set created_at = :created, updated_at = :updated where id = :id")
                    .param("created", utc(created))
                    .param("updated", utc(lastEvent))
                    .param("id", ticket.getId())
                    .update();
            interventionRepository.flush();
            for (Intervention intervention : interventions) {
                jdbc.sql("update interventions set created_at = :at, updated_at = :at where id = :id")
                        .param("at", utc(interventionDates.get(intervention)))
                        .param("id", intervention.getId())
                        .update();
            }
        }

        private void touch(Instant at) {
            if (at.isAfter(lastEvent)) {
                lastEvent = at;
            }
        }
    }

    // ---- Helpers -----------------------------------------------------------------------------------------

    private void loadReferenceData() {
        categories = new HashMap<>();
        categoryRepository.findAllByOrderByNameAsc().forEach(c -> categories.put(c.getName(), c));
        specialists = new HashMap<>();
        DemoCategorySeeder.SPECIALTIES.forEach((email, names) -> userRepository.findByEmail(email).ifPresent(tech ->
                names.forEach(name -> specialists.computeIfAbsent(name, k -> new ArrayList<>()).add(tech))));
        specialists.values().forEach(list -> list.sort(Comparator.comparing(User::getEmail)));
        employees = userRepository.findAll().stream()
                .filter(u -> u.getRoleCode() == RoleCode.EMPLOYEE && u.isActive()
                        && u.getEmail().endsWith("@nexadesk.test"))
                .sorted(Comparator.comparing(User::getEmail))
                .toList();
        sara = userRepository.findByEmail(DemoAccounts.EMPLOYEE_EMAIL).orElseThrow();
        admin = userRepository.findByEmail(DemoAccounts.ADMIN_EMAIL).orElseThrow();
    }

    private Instant add(Instant start, long minutes, SlaSettings.Rule rule) {
        return BusinessTime.addMinutes(start, minutes, sla.calendar(), rule.businessHoursOnly());
    }

    /** The PostgreSQL driver binds OffsetDateTime (not Instant) to timestamptz. */
    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /** Events never happen in the future. */
    private Instant cap(Instant instant) {
        return instant.isAfter(latest) ? latest : instant;
    }

    private static Instant later(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private <T> T pick(List<T> values) {
        return values.get(random.nextInt(values.size()));
    }
}
