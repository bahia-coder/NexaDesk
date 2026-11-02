package com.nexadesk.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.TestcontainersConfiguration;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.notification.dto.NotificationDto;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.RoleRepository;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Real server on a random port, real STOMP client: a notification reaches the connected user in real time. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class WebSocketNotificationIntegrationTest {

    private static final String PASSWORD = "Integration@2026";

    @LocalServerPort
    private int port;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private TicketCategoryRepository categoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private RestClient http;
    private WebSocketStompClient stompClient;
    private StompSession session;
    private long categoryId;
    private String employeeLogin;
    private String technicianLogin;

    @BeforeEach
    void setUp() {
        http = RestClient.create("http://localhost:" + port);
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);

        String run = UUID.randomUUID().toString().substring(0, 8);
        TicketCategory category = categoryRepository.save(new TicketCategory("WS " + run, null, "tag", null));
        categoryId = category.getId();
        employeeLogin = "emp." + run;
        technicianLogin = "tech." + run;
        user(employeeLogin, RoleCode.EMPLOYEE, null);
        user(technicianLogin, RoleCode.TECHNICIAN, category);
    }

    @AfterEach
    void tearDown() {
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
        stompClient.stop();
    }

    private void user(String login, RoleCode role, TicketCategory specialty) {
        User user = User.builder().email(login + "@nexadesk.test").passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("IT").lastName(login).role(roleRepository.findByCode(role).orElseThrow()).build();
        if (specialty != null) {
            user.replaceSpecialties(List.of(specialty));
        }
        userRepository.save(user);
    }

    @SuppressWarnings("unchecked")
    private String token(String login) {
        Map<String, Object> body = http.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", login + "@nexadesk.test", "password", PASSWORD))
                .retrieve().body(Map.class);
        return (String) body.get("accessToken");
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    @Test
    void connectedTechnicianReceivesANewTicketNotificationInRealTime() throws Exception {
        session = connect(token(technicianLogin));
        BlockingQueue<NotificationDto> received = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return NotificationDto.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                received.add((NotificationDto) payload);
            }
        });
        Thread.sleep(300); // let the SUBSCRIBE frame reach the broker before triggering the event

        String employeeToken = token(employeeLogin);
        http.post().uri("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + employeeToken)
                .body(Map.of("type", "INCIDENT", "title", "Souris qui ne répond plus",
                        "description", "La souris ne répond plus depuis ce matin.", "categoryId", categoryId,
                        "impact", "LOW", "urgency", "LOW"))
                .retrieve().toBodilessEntity();

        NotificationDto notification = received.poll(10, TimeUnit.SECONDS);
        assertThat(notification).as("notification pushed over WebSocket").isNotNull();
        assertThat(notification.type()).isEqualTo(NotificationType.TICKET_CREATED);
        assertThat(notification.title()).startsWith("Nouveau ticket INC-");
        assertThat(notification.read()).isFalse();
    }

    @Test
    void connectionWithoutAValidTokenIsRefused() {
        assertThatThrownBy(() -> session = connect(null)).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> session = connect("not.a.jwt")).isInstanceOf(Exception.class);
    }
}
