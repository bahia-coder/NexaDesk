package com.nexadesk.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexadesk.notification.NotificationComposer.Context;
import com.nexadesk.notification.NotificationComposer.Draft;
import com.nexadesk.ticket.TicketEvent;
import com.nexadesk.ticket.TicketEvent.Kind;
import com.nexadesk.ticket.TicketStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationComposerTest {

    private static final long ADMIN = 1;
    private static final long REQUESTER = 10;
    private static final long TECH = 20;
    private static final long OTHER_TECH = 21;
    private static final long COLLEAGUE = 22;

    private static List<Draft> compose(TicketEvent event, Long assigneeId) {
        return NotificationComposer.compose(new Context(event, "INC-2026-0042", "VPN inaccessible", "Haute",
                REQUESTER, assigneeId, "Acteur", assigneeId == null ? null : "Karim", List.of(ADMIN),
                List.of(TECH, COLLEAGUE)));
    }

    private static TicketEvent event(Kind kind, Long actor) {
        return new TicketEvent(kind, 42L, actor, null, null, null);
    }

    private static TicketEvent status(Long actor, TicketStatus from, TicketStatus to) {
        return new TicketEvent(Kind.STATUS_CHANGED, 42L, actor, from, to, null);
    }

    private static List<Long> recipients(List<Draft> drafts) {
        return drafts.stream().map(Draft::recipientId).toList();
    }

    @Test
    void newTicketGoesToAdminsAndSpecialistsButNotToItsAuthor() {
        List<Draft> drafts = compose(event(Kind.CREATED, REQUESTER), null);

        assertThat(recipients(drafts)).containsExactlyInAnyOrder(ADMIN, TECH, COLLEAGUE);
        assertThat(drafts).allMatch(d -> d.type() == NotificationType.TICKET_CREATED);
    }

    @Test
    void ticketOpenedOnBehalfOfAnEmployeeAlsoTellsThem() {
        assertThat(recipients(compose(event(Kind.CREATED, TECH), null)))
                .containsExactlyInAnyOrder(REQUESTER, ADMIN, COLLEAGUE);
    }

    @Test
    void selfAssignmentOnlyTellsTheRequester() {
        assertThat(recipients(compose(new TicketEvent(Kind.ASSIGNED, 42L, TECH, null, null, null), TECH)))
                .containsExactly(REQUESTER);
    }

    @Test
    void reassignmentTellsTheNewAndPreviousTechnicianAndTheRequester() {
        List<Draft> drafts = compose(new TicketEvent(Kind.ASSIGNED, 42L, ADMIN, null, null, OTHER_TECH), TECH);

        assertThat(recipients(drafts)).containsExactlyInAnyOrder(TECH, OTHER_TECH, REQUESTER);
        assertThat(drafts).filteredOn(d -> d.recipientId() == OTHER_TECH).singleElement()
                .satisfies(d -> assertThat(d.message()).contains("Karim"));
    }

    @Test
    void movingToAssignedIsCoveredByTheAssignmentEvent() {
        assertThat(compose(status(TECH, TicketStatus.NEW, TicketStatus.ASSIGNED), TECH)).isEmpty();
    }

    @Test
    void resolutionAsksTheRequesterToConfirm() {
        assertThat(compose(status(TECH, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED), TECH))
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.recipientId()).isEqualTo(REQUESTER);
                    assertThat(d.type()).isEqualTo(NotificationType.TICKET_RESOLVED);
                });
    }

    @Test
    void requesterAnswerToAResolutionGoesToTheTechnician() {
        assertThat(compose(status(REQUESTER, TicketStatus.RESOLVED, TicketStatus.CLOSED), TECH))
                .singleElement().extracting(Draft::type).isEqualTo(NotificationType.RESOLUTION_CONFIRMED);
        assertThat(compose(status(REQUESTER, TicketStatus.RESOLVED, TicketStatus.IN_PROGRESS), TECH))
                .singleElement().extracting(Draft::type).isEqualTo(NotificationType.RESOLUTION_REJECTED);
    }

    @Test
    void automaticClosureOnlyInformsTheRequester() {
        assertThat(recipients(compose(status(null, TicketStatus.RESOLVED, TicketStatus.CLOSED), TECH)))
                .containsExactly(REQUESTER);
    }

    @Test
    void adminStatusChangeAlsoInformsTheAssignee() {
        assertThat(recipients(compose(status(ADMIN, TicketStatus.IN_PROGRESS, TicketStatus.PENDING), TECH)))
                .containsExactlyInAnyOrder(REQUESTER, TECH);
    }

    @Test
    void commentsReachTheOtherSideOfTheConversation() {
        assertThat(recipients(compose(event(Kind.PUBLIC_COMMENT, REQUESTER), TECH))).containsExactly(TECH);
        assertThat(recipients(compose(event(Kind.PUBLIC_COMMENT, TECH), TECH))).containsExactly(REQUESTER);
        assertThat(recipients(compose(event(Kind.PUBLIC_COMMENT, COLLEAGUE), TECH)))
                .containsExactlyInAnyOrder(REQUESTER, TECH);
        assertThat(compose(event(Kind.PUBLIC_COMMENT, REQUESTER), null)).as("nobody assigned yet").isEmpty();
    }

    @Test
    void slaAlertsGoToTheAssigneeAndBreachesAlsoToAdmins() {
        assertThat(recipients(compose(event(Kind.SLA_WARNING, null), TECH))).containsExactly(TECH);
        assertThat(recipients(compose(event(Kind.SLA_WARNING, null), null))).containsExactly(ADMIN);
        assertThat(recipients(compose(event(Kind.SLA_BREACHED, null), TECH))).containsExactlyInAnyOrder(ADMIN, TECH);
    }

    @Test
    void reopenedUnassignedTicketIsOfferedToSpecialists() {
        assertThat(recipients(compose(status(REQUESTER, TicketStatus.CLOSED, TicketStatus.NEW), null)))
                .containsExactlyInAnyOrder(TECH, COLLEAGUE);
    }
}
