package com.nexadesk.demo;

import com.nexadesk.user.RoleCode;
import java.util.List;

/**
 * Documented demonstration credentials (README). Test-only values, used exclusively by the {@code demo}
 * profile; they protect no real data.
 */
public final class DemoAccounts {

    public static final String ADMIN_PASSWORD = "Demo@Admin2026";
    public static final String TECHNICIAN_PASSWORD = "Demo@Tech2026";
    public static final String EMPLOYEE_PASSWORD = "Demo@User2026";

    public static final String ADMIN_EMAIL = "admin@nexadesk.test";
    public static final String TECHNICIAN_EMAIL = "tech.reseau@nexadesk.test";
    public static final String EMPLOYEE_EMAIL = "employe@nexadesk.test";

    public record DemoAccount(RoleCode role, String label, String email, String password) {
    }

    /** One showcase account per role, offered as one-click logins on the login page. */
    public static final List<DemoAccount> SHOWCASE = List.of(
            new DemoAccount(RoleCode.ADMIN, "Administrateur", ADMIN_EMAIL, ADMIN_PASSWORD),
            new DemoAccount(RoleCode.TECHNICIAN, "Technicien", TECHNICIAN_EMAIL, TECHNICIAN_PASSWORD),
            new DemoAccount(RoleCode.EMPLOYEE, "Employé", EMPLOYEE_EMAIL, EMPLOYEE_PASSWORD));

    private DemoAccounts() {
    }
}
