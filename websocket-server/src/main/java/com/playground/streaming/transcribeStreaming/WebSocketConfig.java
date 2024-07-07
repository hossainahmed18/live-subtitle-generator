package com.playground.streaming.transcribeStreaming;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final TranscribeWebSocketHandler transcribeWebSocketHandler;

    public WebSocketConfig(TranscribeWebSocketHandler transcribeWebSocketHandler) {
        this.transcribeWebSocketHandler = transcribeWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(transcribeWebSocketHandler, "/audio-stream")
                .setAllowedOrigins("*");
    }
}
