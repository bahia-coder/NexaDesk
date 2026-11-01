package com.nexadesk.user.dto;

/**
 * Returned once, on creation or reset: the admin passes the temporary password on to the user, who must
 * change it at first login. It is never stored in clear nor retrievable afterwards.
 */
public record TemporaryPasswordResponse(UserDto user, String temporaryPassword) {
}
