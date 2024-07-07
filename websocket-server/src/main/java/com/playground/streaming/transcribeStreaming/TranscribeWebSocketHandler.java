package com.playground.streaming.transcribeStreaming;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.*;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;


@Component
public class TranscribeWebSocketHandler extends BinaryWebSocketHandler {
    private final TranscribeStreamingAsyncClient transcribeClient;
    private StartStreamTranscriptionResponseHandler responseHandler;
    private WebSocketSession currentSession;
    private boolean isTranscriptionStarted = false;


    public TranscribeWebSocketHandler() {
        this.transcribeClient = TranscribeStreamingAsyncClient.builder()
                .credentialsProvider(getCredentials())
                .region(Region.EU_CENTRAL_1)
                .build();

        this.responseHandler = getResponseHandler();
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        if (!isTranscriptionStarted) {
            startTranscriptionSession(message);
        }
    
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        currentSession = session;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        stopTranscriptionSession();
    }

    private void startTranscriptionSession(BinaryMessage initialMessage) throws IOException {
        ByteBuffer byteBuffer = initialMessage.getPayload();
        byte[] bytesArray;
    
        if (byteBuffer.hasArray()) {
            bytesArray = byteBuffer.array();
        } else {
            bytesArray = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytesArray);
        }
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(new String(bytesArray));
        String messageType = jsonNode.get("type").asText();
        String hlsUrl = jsonNode.get("hlsUrl").asText();
    
        if ("startTranscription".equals(messageType)) {
            isTranscriptionStarted = true;
            
            
    
            StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                    .languageCode(LanguageCode.EN_US)
                    .mediaEncoding(MediaEncoding.PCM)
                    .mediaSampleRateHertz(16000)
                    .build();
    
            this.transcribeClient.startStreamTranscription(
                    request,
                    new AudioStreamPublisher(getStreamFromHLS(hlsUrl)),
                    getResponseHandler());
        } else {
            System.out.println("Received message is not for starting transcription.");
        }
    }

    private static InputStream getStreamFromHLS(String hlsUrl)  {
        try {
            String classPath = TranscribeWebSocketHandler.class.getProtectionDomain().getCodeSource().getLocation()
                    .toURI().getPath();

            File classFile = new File(classPath);
            String classDir = classFile.getParent();

            String ffmpegPath = classDir + File.separator + "ffmpeg" + File.separator + "ffmpeg";

            File ffmpegFile = new File(ffmpegPath);
            if (!ffmpegFile.exists()) {
                throw new FileNotFoundException("ffmpeg binary not found at " + ffmpegPath);
            }
            if (!ffmpegFile.canExecute()) {
                throw new SecurityException("ffmpeg binary is not executable");
            }

            List<String> command = Arrays.asList(
                ffmpegPath,
                "-re", 
                "-i", hlsUrl,
                "-f", "s16le",
                "-ar", "16000",
                "-ac", "1",
                "-acodec", "pcm_s16le",
                "-");
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            return process.getInputStream();
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to start ffmpeg process", e);
        }
    }

    private void stopTranscriptionSession() {
        isTranscriptionStarted = false;
    }

    private StartStreamTranscriptionResponseHandler getResponseHandler() {

        return StartStreamTranscriptionResponseHandler.builder()
                .onResponse(r -> {
                    try {
                        System.out.println("Received Initial response");
                        currentSession.sendMessage(new TextMessage("##Transcript##"+"**backend initialized"+"##partial##"+"true"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .onError(e -> System.err.println("Error: " + e.getMessage()))
                .onComplete(() -> System.out.println("Transcription completed"))
                .subscriber(event -> {
                    List<Result> results = ((TranscriptEvent) event).transcript().results();
                    if (results.size() > 0) {
                        Result firstResult = results.get(0);
                        if (firstResult.alternatives().size() > 0
                                && !firstResult.alternatives().get(0).transcript().isEmpty()) {
                            String transcript = firstResult.alternatives().get(0).transcript();
                            if (!transcript.isEmpty()) {
                                String displayText = "##Transcript##"+transcript+"##partial##"+firstResult.isPartial();
                                try {
                                    currentSession.sendMessage(new TextMessage(displayText));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }

                    }
                })
                .build();
    }

    private static AwsCredentialsProvider getCredentials() {
        String profileName = "eu-north-1-nonprod";
        return ProfileCredentialsProvider.builder()
                .profileName(profileName)
                .build();
    }
}