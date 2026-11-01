package com.nexadesk.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.TestcontainersConfiguration;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.RoleRepository;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Category, priority and specialty endpoints with real JWTs against PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoryIntegrationTest {

    private static final String PASSWORD = "Integration@2026";
    private static final String ADMIN = "it.cat.admin@nexadesk.test";
    private static final String TECH = "it.cat.tech@nexadesk.test";
    private static final String EMPLOYEE = "it.cat.employee@nexadesk.test";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        for (String email : List.of(ADMIN, TECH, EMPLOYEE)) {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
        create(ADMIN, RoleCode.ADMIN);
        create(TECH, RoleCode.TECHNICIAN);
        create(EMPLOYEE, RoleCode.EMPLOYEE);
        adminToken = token(ADMIN);
    }

    private void create(String email, RoleCode role) {
        userRepository.save(User.builder().email(email).passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("IT").lastName(role.name()).role(roleRepository.findByCode(role).orElseThrow()).build());
    }

    private String token(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    /** Unique per test run: the database is shared by the integration test classes. */
    private static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private long createCategory(String name) throws Exception {
        String body = mockMvc.perform(as(adminToken, post("/api/categories"))
                        .content(json(Map.of("name", name, "icon", "wifi", "keywords", "Réseau, VPN"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keywords").value("réseau, vpn"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void adminManagesCategoriesAndSubcategories() throws Exception {
        String name = uniqueName("Téléphonie");
        long id = createCategory(name);

        mockMvc.perform(as(adminToken, post("/api/categories")).content(json(Map.of("name", name.toUpperCase()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_NAME"));

        mockMvc.perform(as(adminToken, post("/api/categories/{id}/subcategories", id))
                        .content(json(Map.of("name", "Standard"))))
                .andExpect(status().isCreated());
        mockMvc.perform(as(adminToken, post("/api/categories/{id}/subcategories", id))
                        .content(json(Map.of("name", "standard"))))
                .andExpect(status().isConflict());

        mockMvc.perform(as(adminToken, put("/api/categories/{id}", id))
                        .content(json(Map.of("name", name, "icon", "Not An Icon"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("icon"));
    }

    @Test
    void employeesOnlySeeActiveCategoriesAndCannotManageThem() throws Exception {
        String name = uniqueName("Fax");
        long id = createCategory(name);
        mockMvc.perform(as(adminToken, post("/api/categories/{id}/deactivate", id)))
                .andExpect(jsonPath("$.active").value(false));

        String employee = token(EMPLOYEE);
        mockMvc.perform(as(employee, get("/api/categories")).param("includeInactive", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '" + name + "')]").isEmpty());
        mockMvc.perform(as(adminToken, get("/api/categories")).param("includeInactive", "true"))
                .andExpect(jsonPath("$[?(@.name == '" + name + "')]").isNotEmpty());

        mockMvc.perform(as(employee, post("/api/categories")).content(json(Map.of("name", "Hack"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void prioritiesAreOrderedByRankAndOnlyLabelAndColorAreEditable() throws Exception {
        mockMvc.perform(as(token(EMPLOYEE), get("/api/priorities")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("LOW"))
                .andExpect(jsonPath("$[3].code").value("CRITICAL"));

        mockMvc.perform(as(token(EMPLOYEE), get("/api/priorities/matrix")))
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[?(@.impact == 'HIGH' && @.urgency == 'HIGH')].priority").value("CRITICAL"));

        String body = mockMvc.perform(as(adminToken, get("/api/priorities"))).andReturn().getResponse()
                .getContentAsString();
        long lowId = objectMapper.readTree(body).get(0).get("id").asLong();
        mockMvc.perform(as(adminToken, put("/api/priorities/{id}", lowId))
                        .content(json(Map.of("label", "Basse", "color", "#64748b"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#64748B"))
                .andExpect(jsonPath("$.code").value("LOW"));
    }

    @Test
    void adminAssignsSpecialtiesAndTechniciansCanBeFilteredByThem() throws Exception {
        long categoryId = createCategory(uniqueName("Réseau IT"));
        long techId = userRepository.findByEmail(TECH).orElseThrow().getId();
        long employeeId = userRepository.findByEmail(EMPLOYEE).orElseThrow().getId();

        mockMvc.perform(as(adminToken, put("/api/users/{id}/specialties", techId))
                        .content(json(Map.of("categoryIds", List.of(categoryId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(categoryId));

        mockMvc.perform(as(adminToken, put("/api/users/{id}/specialties", employeeId))
                        .content(json(Map.of("categoryIds", List.of(categoryId)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SPECIALTIES_TECHNICIAN_ONLY"));

        mockMvc.perform(as(token(TECH), get("/api/users/technicians")).param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(TECH))
                .andExpect(jsonPath("$[0].specialties[0].id").value(categoryId));

        mockMvc.perform(as(token(EMPLOYEE), get("/api/users/technicians")))
                .andExpect(status().isForbidden());

        // Demoting the technician removes their specialties.
        mockMvc.perform(as(adminToken, put("/api/users/{id}/role", techId)).content(json(Map.of("role", "EMPLOYEE"))))
                .andExpect(status().isOk());
        mockMvc.perform(as(adminToken, get("/api/users/{id}/specialties", techId)))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
