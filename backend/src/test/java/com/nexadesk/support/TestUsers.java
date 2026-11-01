package com.nexadesk.support;

import com.nexadesk.user.Role;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** Builds detached entities for unit tests (no database, so ids and roles are set by reflection). */
public final class TestUsers {

    private TestUsers() {
    }

    public static Role role(RoleCode code) {
        Role role = BeanUtils.instantiateClass(Role.class);
        ReflectionTestUtils.setField(role, "code", code);
        ReflectionTestUtils.setField(role, "label", code.name());
        return role;
    }

    public static User user(long id, String email, RoleCode roleCode, String passwordHash) {
        User user = User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .firstName("Test")
                .lastName("User")
                .role(role(roleCode))
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
