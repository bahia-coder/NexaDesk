package com.nexadesk.ticket;

/**
 * Everything a user can do on a ticket. The API returns the subset available to the current user
 * ({@code availableActions}), so the frontend shows exactly the buttons the backend will accept.
 */
public enum TicketAction {
    EDIT,
    RECLASSIFY,
    TAKE,
    ASSIGN,
    START,
    PUT_ON_HOLD,
    RESUME,
    RESOLVE,
    REJECT,
    CLOSE,
    CONFIRM_RESOLUTION,
    REJECT_RESOLUTION,
    REOPEN,
    COMMENT,
    COMMENT_INTERNAL,
    ADD_ATTACHMENT,
    LOG_INTERVENTION,
    GIVE_FEEDBACK
}
