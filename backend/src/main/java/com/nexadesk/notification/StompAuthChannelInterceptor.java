package com.nexadesk.notification;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

/**
 * Security of the STOMP channel. Browsers cannot add an Authorization header to the WebSocket handshake, so the
 * access token is sent in the STOMP CONNECT frame and validated here with the same decoder as the REST API.
 *
 * <ul>
 *   <li>CONNECT without a valid token is refused (the client receives an ERROR frame).</li>
 *   <li>SUBSCRIBE is only allowed on the user's own queue ({@code /user/queue/...}): Spring resolves it to the
 *       session's principal, so nobody can listen to someone else's notifications.</li>
 *   <li>SEND is refused: the channel is server-to-client only.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (accessor.getUser() == null || destination == null || !destination.startsWith("/user/queue/")) {
                throw new MessageDeliveryException("Abonnement non autorisé : " + destination);
            }
        } else if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("Ce canal est en lecture seule.");
        }
        return message;
    }

    private AbstractAuthenticationToken authenticate(List<String> authorization) {
        if (authorization == null || authorization.isEmpty() || !authorization.getFirst().startsWith(BEARER)) {
            throw new MessageDeliveryException("Jeton d'accès manquant.");
        }
        try {
            return jwtAuthenticationConverter.convert(jwtDecoder.decode(authorization.getFirst().substring(BEARER.length())));
        } catch (JwtException e) {
            throw new MessageDeliveryException("Jeton d'accès invalide ou expiré.");
        }
    }
}
