package io.grouptab.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor websocketAuthInterceptor;

    @Value("${rabbitmq.stomp.host:rabbitmq}")
    private String relayHost;

    @Value("${stomp.port:61613}")
    private int relayPort;

    @Value("${rabbitmq.stomp.login:guest}")
    private String login;

    @Value("${rabbitmq.stomp.passcode:guest}")
    private String passcode;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setClientLogin(login)
                .setClientPasscode(passcode)
                .setSystemLogin(login)
                .setSystemPasscode(passcode);
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        //TODO Replace allowedOriginPatterns
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(websocketAuthInterceptor);
    }
}
