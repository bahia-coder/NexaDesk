package com.nexadesk.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.TestcontainersConfiguration;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.RoleRepository;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end authentication flow against a real PostgreSQL (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    private static final String EMAIL = "it.auth@nexadesk.test";
    private static final String PASSWORD = "Integration@2026";
    private static final String COOKIE = "nexadesk_refresh";
    private static final String ALLOWED_ORIGIN = "http://localhost:4200";

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
    void createUser() {
        userRepository.findByEmail(EMAIL).ifPresent(userRepository::delete);
        userRepository.save(User.builder()
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("Inès")
                .lastName("Test")
                .role(roleRepository.findByCode(RoleCode.TECHNICIAN).orElseThrow())
                .build());
    }

    private MvcResult login(String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }

    private static String refreshCookieValue(MvcResult result) {
        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.substring(COOKIE.length() + 1, setCookie.indexOf(';'));
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    @Test
    void loginReturnsBearerTokenAndHardenedRefreshCookie() throws Exception {
        MvcResult result = login(PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("user").get("role").asText()).isEqualTo("TECHNICIAN");
        assertThat(body.get("user").has("passwordHash")).isFalse();
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(COOKIE + "=")
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Secure");
    }

    @Test
    void wrongPasswordReturnsProblemDetailWithCode() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.traceId").exists());

        assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getFailedLoginAttempts())
                .as("failed attempt must be committed despite the exception")
                .isEqualTo(1);
    }

    @Test
    void invalidLoginBodyReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void meRequiresAValidBearerToken() throws Exception {
        String token = accessToken(login(PASSWORD));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.fullName").value("Inès Test"));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTheCookieAndRejectsTheOldOne() throws Exception {
        String firstCookie = refreshCookieValue(login(PASSWORD));

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .cookie(new Cookie(COOKIE, firstCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        String secondCookie = refreshCookieValue(refreshed);
        assertThat(secondCookie).isNotEqualTo(firstCookie);

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, firstCookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // Within the grace period the reuse is treated as a concurrent refresh: the new token still works.
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, secondCookie)))
                .andExpect(status().isOk());
    }

    @Test
    void refreshFromAForeignOriginIsRejected() throws Exception {
        String cookie = refreshCookieValue(login(PASSWORD));

        mockMvc.perform(post("/api/auth/refresh")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .cookie(new Cookie(COOKIE, cookie)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        String cookie = refreshCookieValue(login(PASSWORD));

        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(COOKIE, cookie)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, cookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void demoAccountsEndpointDoesNotExistOutsideDemoProfile() throws Exception {
        mockMvc.perform(get("/api/auth/demo-accounts"))
                .andExpect(status().isNotFound());
    }
}
