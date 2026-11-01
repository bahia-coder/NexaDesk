package com.nexadesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** User administration and profile endpoints, with real JWTs, against PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserManagementIntegrationTest {

    private static final String PASSWORD = "Integration@2026";
    private static final String ADMIN = "it.mgmt.admin@nexadesk.test";
    private static final String EMPLOYEE = "it.mgmt.employee@nexadesk.test";
    private static final String CREATED = "it.mgmt.created@nexadesk.test";

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

    @BeforeEach
    void resetUsers() {
        for (String email : new String[] {ADMIN, EMPLOYEE, CREATED}) {
            userRepository.findByEmail(email).ifPresent(userRepository::delete);
        }
        create(ADMIN, RoleCode.ADMIN, "Alice", "Admin");
        create(EMPLOYEE, RoleCode.EMPLOYEE, "Eric", "Employé");
    }

    private void create(String email, RoleCode role, String firstName, String lastName) {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName(firstName)
                .lastName(lastName)
                .role(roleRepository.findByCode(role).orElseThrow())
                .build());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andReturn();
    }

    private String token(String email, String password) throws Exception {
        return field(login(email, password), "accessToken");
    }

    private String field(MvcResult result, String name) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(name).asText();
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static MockHttpServletRequestBuilder bearer(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void employeeCannotReachUserAdministration() throws Exception {
        mockMvc.perform(bearer(get("/api/users"), token(EMPLOYEE, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminListsUsersWithCombinableFiltersPaginationAndSort() throws Exception {
        String admin = token(ADMIN, PASSWORD);

        mockMvc.perform(bearer(get("/api/users"), admin)
                        .param("search", "it.mgmt")
                        .param("role", "EMPLOYEE")
                        .param("active", "true")
                        .param("size", "5")
                        .param("sort", "email,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content[0].email").value(EMPLOYEE))
                .andExpect(jsonPath("$.content[0].role").value("EMPLOYEE"))
                .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());

        // Search matches the full name too, and LIKE wildcards in the input are escaped.
        mockMvc.perform(bearer(get("/api/users"), admin).param("search", "alice admin"))
                .andExpect(jsonPath("$.content[0].email").value(ADMIN));
        mockMvc.perform(bearer(get("/api/users"), admin).param("search", "it.mgmt%"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sortingOnANonWhitelistedPropertyIsRejected() throws Exception {
        mockMvc.perform(bearer(get("/api/users"), token(ADMIN, PASSWORD)).param("sort", "passwordHash"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT"));
    }

    @Test
    void createdUserMustChangeTheTemporaryPasswordBeforeUsingTheApi() throws Exception {
        MvcResult created = mockMvc.perform(bearer(post("/api/users"), token(ADMIN, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", CREATED, "firstName", "Nora", "lastName", "Nouvelle",
                                "role", "EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn();
        String temporaryPassword = field(created, "temporaryPassword");

        MvcResult firstLogin = login(CREATED, temporaryPassword);
        JsonNode body = objectMapper.readTree(firstLogin.getResponse().getContentAsString());
        assertThat(body.get("user").get("mustChangePassword").asBoolean()).isTrue();
        String restricted = body.get("accessToken").asText();

        mockMvc.perform(bearer(get("/api/departments"), restricted))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(bearer(get("/api/profile"), restricted))
                .andExpect(status().isOk());

        mockMvc.perform(bearer(put("/api/profile/password"), restricted)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", temporaryPassword, "newPassword", "weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("newPassword"));

        MvcResult changed = mockMvc.perform(bearer(put("/api/profile/password"), restricted)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", temporaryPassword, "newPassword", "Nora#Secure2026"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                .andReturn();
        assertThat(changed.getResponse().getHeader(HttpHeaders.SET_COOKIE)).startsWith("nexadesk_refresh=");

        mockMvc.perform(bearer(get("/api/departments"), field(changed, "accessToken")))
                .andExpect(status().isOk());
    }

    @Test
    void creatingAUserWithAnExistingEmailIsAConflict() throws Exception {
        mockMvc.perform(bearer(post("/api/users"), token(ADMIN, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMPLOYEE.toUpperCase(), "firstName", "X", "lastName", "Y",
                                "role", "EMPLOYEE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void adminCannotDeactivateThemself() throws Exception {
        mockMvc.perform(bearer(post("/api/users/{id}/deactivate", userId(ADMIN)), token(ADMIN, PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SELF_MODIFICATION_FORBIDDEN"));
    }

    @Test
    void deactivatedUserCannotLogInAndLosesExistingSessions() throws Exception {
        String refreshCookie = login(EMPLOYEE, PASSWORD).getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String cookieValue = refreshCookie.substring("nexadesk_refresh=".length(), refreshCookie.indexOf(';'));

        mockMvc.perform(bearer(post("/api/users/{id}/deactivate", userId(EMPLOYEE)), token(ADMIN, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(login(EMPLOYEE, PASSWORD).getResponse().getStatus()).isEqualTo(403);
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("nexadesk_refresh", cookieValue)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersCanEditTheirOwnProfile() throws Exception {
        mockMvc.perform(bearer(put("/api/profile"), token(EMPLOYEE, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("jobTitle", "Comptable senior", "phone", "+33 6 12 34 56 78",
                                "avatarColor", "#0891b2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Comptable senior"))
                .andExpect(jsonPath("$.avatarColor").value("#0891B2"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }
}
