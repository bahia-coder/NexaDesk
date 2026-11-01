package com.nexadesk.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nexadesk.category.TicketCategory;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.support.TestUsers;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

class TicketPolicyTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    private final TicketPolicy policy = new TicketPolicy(new TicketProperties(Duration.ofDays(7), 5));

    private TicketCategory network;
    private User requester;
    private User otherEmployee;
    private User specialist;
    private User otherTechnician;
    private User admin;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        network = BeanUtils.instantiateClass(TicketCategory.class);
        ReflectionTestUtils.setField(network, "id", 1L);
        requester = TestUsers.user(10, "req@nexadesk.test", RoleCode.EMPLOYEE, "x");
        otherEmployee = TestUsers.user(11, "other@nexadesk.test", RoleCode.EMPLOYEE, "x");
        specialist = TestUsers.user(20, "net@nexadesk.test", RoleCode.TECHNICIAN, "x");
        specialist.replaceSpecialties(List.of(network));
        otherTechnician = TestUsers.user(21, "hw@nexadesk.test", RoleCode.TECHNICIAN, "x");
        admin = TestUsers.user(1, "admin@nexadesk.test", RoleCode.ADMIN, "x");
        ticket = Ticket.builder().reference("INC-2026-0001").category(network).requester(requester).build();
    }

    private void status(TicketStatus status, User assignee) {
        ticket.setStatus(status);
        ticket.setAssignee(assignee);
    }

    @Test
    void visibilityFollowsRoleOwnershipAndSpecialty() {
        assertThat(policy.canView(ticket, requester)).isTrue();
        assertThat(policy.canView(ticket, admin)).isTrue();
        assertThat(policy.canView(ticket, specialist)).as("technician of the ticket's category").isTrue();
        assertThat(policy.canView(ticket, otherEmployee)).isFalse();
        assertThat(policy.canView(ticket, otherTechnician)).isFalse();

        status(TicketStatus.ASSIGNED, otherTechnician);
        assertThat(policy.canView(ticket, otherTechnician)).as("assignee outside their specialty").isTrue();
    }

    @Test
    void requesterOnNewTicketCanEditAndCommentButNotWorkIt() {
        assertThat(policy.availableActions(ticket, requester, NOW))
                .containsExactlyInAnyOrder(TicketAction.EDIT, TicketAction.COMMENT, TicketAction.ADD_ATTACHMENT);
    }

    @Test
    void onlyASpecialistCanTakeANewUnassignedTicket() {
        assertThat(policy.availableActions(ticket, specialist, NOW))
                .contains(TicketAction.TAKE, TicketAction.COMMENT_INTERNAL)
                .doesNotContain(TicketAction.START, TicketAction.EDIT);
        assertThat(policy.availableActions(ticket, admin, NOW))
                .contains(TicketAction.ASSIGN, TicketAction.REJECT)
                .doesNotContain(TicketAction.TAKE);

        status(TicketStatus.ASSIGNED, otherTechnician);
        assertThat(policy.availableActions(ticket, specialist, NOW)).doesNotContain(TicketAction.TAKE);
    }

    @Test
    void requesterCanNoLongerEditOnceTheTicketIsAssigned() {
        status(TicketStatus.ASSIGNED, specialist);
        assertThat(policy.availableActions(ticket, requester, NOW)).doesNotContain(TicketAction.EDIT);
        assertThat(policy.availableActions(ticket, specialist, NOW))
                .contains(TicketAction.EDIT, TicketAction.START, TicketAction.REJECT, TicketAction.RECLASSIFY);
    }

    @Test
    void handlerTransitionsDependOnTheCurrentStatus() {
        status(TicketStatus.IN_PROGRESS, specialist);
        assertThat(policy.availableActions(ticket, specialist, NOW))
                .contains(TicketAction.PUT_ON_HOLD, TicketAction.RESOLVE)
                .doesNotContain(TicketAction.START, TicketAction.RESUME, TicketAction.REJECT, TicketAction.CLOSE);

        status(TicketStatus.PENDING, specialist);
        assertThat(policy.availableActions(ticket, specialist, NOW))
                .contains(TicketAction.RESUME)
                .doesNotContain(TicketAction.RESOLVE, TicketAction.PUT_ON_HOLD);
    }

    @Test
    void interventionsAreLoggedByTheHandlerOnStartedTicketsOnly() {
        status(TicketStatus.ASSIGNED, specialist);
        assertThat(policy.availableActions(ticket, specialist, NOW)).doesNotContain(TicketAction.LOG_INTERVENTION);

        status(TicketStatus.IN_PROGRESS, specialist);
        assertThat(policy.availableActions(ticket, specialist, NOW)).contains(TicketAction.LOG_INTERVENTION);
        assertThat(policy.availableActions(ticket, admin, NOW)).contains(TicketAction.LOG_INTERVENTION);
        assertThat(policy.availableActions(ticket, requester, NOW)).doesNotContain(TicketAction.LOG_INTERVENTION);

        status(TicketStatus.PENDING, otherTechnician);
        assertThat(policy.availableActions(ticket, specialist, NOW))
                .as("same specialty but not the assignee")
                .doesNotContain(TicketAction.LOG_INTERVENTION);
    }

    @Test
    void onlyTheRequesterConfirmsOrRejectsAResolution() {
        status(TicketStatus.RESOLVED, specialist);
        assertThat(policy.availableActions(ticket, requester, NOW))
                .contains(TicketAction.CONFIRM_RESOLUTION, TicketAction.REJECT_RESOLUTION, TicketAction.GIVE_FEEDBACK);
        assertThat(policy.availableActions(ticket, specialist, NOW))
                .doesNotContain(TicketAction.CONFIRM_RESOLUTION, TicketAction.GIVE_FEEDBACK);
        assertThat(policy.availableActions(ticket, admin, NOW)).contains(TicketAction.CLOSE);
    }

    @Test
    void closedTicketIsReadOnlyExceptForReopenAndFeedback() {
        status(TicketStatus.CLOSED, specialist);
        ticket.setClosedAt(NOW.minus(Duration.ofDays(2)));

        assertThat(policy.availableActions(ticket, requester, NOW))
                .containsExactlyInAnyOrder(TicketAction.REOPEN, TicketAction.GIVE_FEEDBACK);
        assertThat(policy.availableActions(ticket, specialist, NOW)).isEmpty();
        assertThatThrownBy(() -> policy.require(TicketAction.EDIT, ticket, admin, NOW))
                .hasMessageContaining("fermé")
                .extracting("code").isEqualTo(ErrorCode.ACTION_NOT_ALLOWED);
    }

    @Test
    void requesterCanReopenOnlyWithinTheWindowWhileAnAdminAlwaysCan() {
        status(TicketStatus.CLOSED, specialist);
        ticket.setClosedAt(NOW.minus(Duration.ofDays(8)));

        assertThat(policy.availableActions(ticket, requester, NOW)).doesNotContain(TicketAction.REOPEN);
        assertThat(policy.availableActions(ticket, admin, NOW)).contains(TicketAction.REOPEN);
    }

    @Test
    void rejectedTicketCannotBeReopened() {
        status(TicketStatus.REJECTED, null);
        ticket.setRejectionReason("Doublon");

        assertThat(policy.availableActions(ticket, admin, NOW)).isEmpty();
    }

    @Test
    void invisibleTicketYieldsNoActionAndRequireFails() {
        assertThat(policy.availableActions(ticket, otherEmployee, NOW)).isEmpty();
        assertThatThrownBy(() -> policy.require(TicketAction.COMMENT, ticket, otherEmployee, NOW))
                .extracting("code").isEqualTo(ErrorCode.ACTION_NOT_ALLOWED);
    }

    @Test
    void statusTargetsMapToWorkflowActions() {
        assertThat(TicketWorkflowService.actionFor(TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS))
                .isEqualTo(TicketAction.START);
        assertThat(TicketWorkflowService.actionFor(TicketStatus.PENDING, TicketStatus.IN_PROGRESS))
                .isEqualTo(TicketAction.RESUME);
        assertThatThrownBy(() -> TicketWorkflowService.actionFor(TicketStatus.NEW, TicketStatus.ASSIGNED))
                .extracting("code").isEqualTo(ErrorCode.ACTION_NOT_ALLOWED);
    }
}
