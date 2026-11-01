package com.nexadesk.ticket;

import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.security.CurrentUser;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Loads the acting user and the tickets they may see. A ticket outside the user's visibility is reported as
 * not found (404), not forbidden: its existence is not disclosed.
 */
@Component
@RequiredArgsConstructor
public class TicketAccess {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final TicketPolicy policy;
    private final CurrentUser currentUser;

    public User actor() {
        return userRepository.findWithRoleById(currentUser.id())
                .filter(User::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public Ticket visibleTicket(Long id, User actor) {
        return ticketRepository.findWithDetailsById(id)
                .filter(ticket -> policy.canView(ticket, actor))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));
    }

    public Ticket visibleTicket(String reference, User actor) {
        return ticketRepository.findWithDetailsByReference(reference)
                .filter(ticket -> policy.canView(ticket, actor))
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", reference));
    }

    /** Technicians and admins see internal notes and internal history entries. */
    public static boolean isStaff(User user) {
        return user.getRoleCode() == RoleCode.TECHNICIAN || user.getRoleCode() == RoleCode.ADMIN;
    }
}
