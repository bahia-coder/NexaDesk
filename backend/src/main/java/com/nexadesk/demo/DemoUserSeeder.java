package com.nexadesk.demo;

import com.nexadesk.user.Department;
import com.nexadesk.user.DepartmentRepository;
import com.nexadesk.user.Role;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.RoleRepository;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo profile only: 1 admin, 5 technicians, 15 employees across 6 departments.
 * Idempotent: does nothing if the admin account already exists.
 */
@Slf4j
@Component
@Profile("demo")
@Order(1)
@RequiredArgsConstructor
public class DemoUserSeeder implements ApplicationRunner {

    private record Person(String email, String firstName, String lastName, String jobTitle, String department,
                          String phone) {
    }

    private static final List<String> DEPARTMENTS = List.of(
            "Informatique", "Direction", "Finance", "Ressources humaines", "Commercial", "Marketing");

    private static final List<String> AVATAR_COLORS = List.of(
            "#4F46E5", "#0891B2", "#059669", "#D97706", "#DB2777", "#7C3AED", "#2563EB", "#DC2626");

    private static final Person ADMIN = new Person(DemoAccounts.ADMIN_EMAIL, "Amina", "Berrada",
            "Responsable du service IT", "Informatique", "+33 1 84 60 10 01");

    private static final List<Person> TECHNICIANS = List.of(
            new Person(DemoAccounts.TECHNICIAN_EMAIL, "Karim", "Belhadj", "Technicien réseau & VPN",
                    "Informatique", "+33 1 84 60 10 11"),
            new Person("tech.materiel@nexadesk.test", "Sophie", "Laurent", "Technicienne matériel",
                    "Informatique", "+33 1 84 60 10 12"),
            new Person("tech.logiciel@nexadesk.test", "Youssef", "El Amrani", "Technicien applications",
                    "Informatique", "+33 1 84 60 10 13"),
            new Person("tech.acces@nexadesk.test", "Claire", "Dubois", "Technicienne comptes & accès",
                    "Informatique", "+33 1 84 60 10 14"),
            new Person("tech.messagerie@nexadesk.test", "Mehdi", "Haddad", "Technicien messagerie & collaboration",
                    "Informatique", "+33 1 84 60 10 15"));

    private static final List<Person> EMPLOYEES = List.of(
            new Person(DemoAccounts.EMPLOYEE_EMAIL, "Sara", "Alaoui", "Comptable", "Finance", "+33 1 84 60 20 01"),
            new Person("julien.martin@nexadesk.test", "Julien", "Martin", "Directeur général", "Direction", null),
            new Person("nadia.benjelloun@nexadesk.test", "Nadia", "Benjelloun", "Assistante de direction",
                    "Direction", "+33 1 84 60 20 03"),
            new Person("thomas.petit@nexadesk.test", "Thomas", "Petit", "Contrôleur de gestion", "Finance", null),
            new Person("ines.chraibi@nexadesk.test", "Inès", "Chraibi", "Responsable paie", "Finance", null),
            new Person("lucas.bernard@nexadesk.test", "Lucas", "Bernard", "Chargé de recrutement",
                    "Ressources humaines", "+33 1 84 60 20 06"),
            new Person("salma.tazi@nexadesk.test", "Salma", "Tazi", "Responsable RH", "Ressources humaines", null),
            new Person("emma.moreau@nexadesk.test", "Emma", "Moreau", "Gestionnaire formation",
                    "Ressources humaines", null),
            new Person("hugo.lefebvre@nexadesk.test", "Hugo", "Lefebvre", "Directeur commercial", "Commercial",
                    "+33 1 84 60 20 09"),
            new Person("omar.bennani@nexadesk.test", "Omar", "Bennani", "Ingénieur commercial", "Commercial", null),
            new Person("chloe.girard@nexadesk.test", "Chloé", "Girard", "Ingénieure commerciale", "Commercial", null),
            new Person("rachid.ouali@nexadesk.test", "Rachid", "Ouali", "Administrateur des ventes", "Commercial",
                    null),
            new Person("lea.roux@nexadesk.test", "Léa", "Roux", "Responsable marketing", "Marketing",
                    "+33 1 84 60 20 13"),
            new Person("yasmine.kabbaj@nexadesk.test", "Yasmine", "Kabbaj", "Chargée de communication", "Marketing",
                    null),
            new Person("antoine.fournier@nexadesk.test", "Antoine", "Fournier", "Graphiste", "Marketing", null));

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(DemoAccounts.ADMIN_EMAIL)) {
            log.info("Demo users already present, skipping");
            return;
        }

        Map<String, Department> departments = new LinkedHashMap<>();
        DEPARTMENTS.forEach(name -> departments.put(name, departmentRepository.findByName(name)
                .orElseGet(() -> departmentRepository.save(new Department(name)))));

        Map<RoleCode, Role> roles = new EnumMap<>(RoleCode.class);
        for (RoleCode code : RoleCode.values()) {
            roles.put(code, roleRepository.findByCode(code).orElseThrow());
        }

        // BCrypt is deliberately slow: hash each distinct demo password once.
        String adminHash = passwordEncoder.encode(DemoAccounts.ADMIN_PASSWORD);
        String technicianHash = passwordEncoder.encode(DemoAccounts.TECHNICIAN_PASSWORD);
        String employeeHash = passwordEncoder.encode(DemoAccounts.EMPLOYEE_PASSWORD);

        int[] colorIndex = {0};
        save(ADMIN, roles.get(RoleCode.ADMIN), adminHash, departments, colorIndex);
        TECHNICIANS.forEach(p -> save(p, roles.get(RoleCode.TECHNICIAN), technicianHash, departments, colorIndex));
        EMPLOYEES.forEach(p -> save(p, roles.get(RoleCode.EMPLOYEE), employeeHash, departments, colorIndex));

        log.info("Demo users created: 1 admin, {} technicians, {} employees", TECHNICIANS.size(), EMPLOYEES.size());
    }

    private void save(Person person, Role role, String passwordHash, Map<String, Department> departments,
                      int[] colorIndex) {
        userRepository.save(User.builder()
                .email(person.email())
                .passwordHash(passwordHash)
                .firstName(person.firstName())
                .lastName(person.lastName())
                .jobTitle(person.jobTitle())
                .phone(person.phone())
                .avatarColor(AVATAR_COLORS.get(colorIndex[0]++ % AVATAR_COLORS.size()))
                .role(role)
                .department(departments.get(person.department()))
                .build());
    }
}
