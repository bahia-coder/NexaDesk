package com.nexadesk.user.dto;

import com.nexadesk.user.RoleCode;

/** Optional, combinable filters of the user list (bound from query parameters). */
public record UserFilter(String search, RoleCode role, Boolean active, Long departmentId) {
}
