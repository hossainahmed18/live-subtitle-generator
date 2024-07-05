package com.playground.streaming.transcribeStreaming;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TranscribeStreamingClientWrapper {

    private TranscribeStreamingRetryClient client;
    private AudioStreamPublisher requestStream;

    public TranscribeStreamingClientWrapper() {
        client = new TranscribeStreamingRetryClient(getClient());
    }

    public static TranscribeStreamingAsyncClient getClient() {
        return TranscribeStreamingAsyncClient.builder()
            .credentialsProvider(getCredentials())
            .region(getRegion())
            .build();

    }

    private static Region getRegion() {
        Region region;
        region = Region.EU_CENTRAL_1;
        return region;
    }

    public CompletableFuture<Void> startTranscription(StreamTranscriptionBehavior responseHandler, String inputFile) {
        if (requestStream != null) {
            throw new IllegalStateException("Stream is already open");
        }
        try {
            int sampleRate = 16_000;
            if (inputFile != null && !inputFile.trim().isEmpty()) {
                requestStream = new AudioStreamPublisher(getStreamFromHLS(inputFile));
            } else {
                requestStream = new AudioStreamPublisher(getStreamFromMic());
            }
            return client.startStreamTranscription(
                    getRequest(sampleRate),
                    requestStream,
                    responseHandler);
        } catch (LineUnavailableException ex) {
            CompletableFuture<Void> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(ex);
            return failedFuture;
        }
    }

    public static InputStream getStreamFromHLS(String hlsUrl) {
        try {
            String classPath;
            classPath = TranscribeStreamingApp.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
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
        } catch (IOException e) {
            throw new RuntimeException("Failed to start ffmpeg process", e);
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Failed to start ffmpeg process", e);
        }
    }


    public void stopTranscription() {
        if (requestStream != null) {
            try {
                requestStream.inputStream.close();
            } catch (IOException ex) {
                System.out.println("Error stopping input stream: " + ex);
            } finally {
                requestStream = null;
            }
        }
    }

   
    public void close() {
        try {
            if (requestStream != null) {
                requestStream.inputStream.close();
            }
        } catch (IOException ex) {
            System.out.println("error closing in-progress microphone stream: " + ex);
        } finally {
            client.close();
        }
    }

 
    private static InputStream getStreamFromMic() throws LineUnavailableException {


        int sampleRate = 16000;
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            System.out.println("Line not supported");
            System.exit(0);
        }

        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(format);
        line.start();

        return new AudioInputStream(line);
    }

    private StartStreamTranscriptionRequest getRequest(Integer mediaSampleRateHertz) {
        return StartStreamTranscriptionRequest.builder()
                .languageCode(LanguageCode.EN_US.toString())
                .mediaEncoding(MediaEncoding.PCM)
                .mediaSampleRateHertz(mediaSampleRateHertz)
                .build();
    }
    private static AwsCredentialsProvider getCredentials() {
        String profileName = "eu-north-1-nonprod";
        return ProfileCredentialsProvider.builder()
                .profileName(profileName)
                .build();
    }

   
    private static class AudioStreamPublisher implements Publisher<AudioStream> {
        private final InputStream inputStream;

        private AudioStreamPublisher(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void subscribe(Subscriber<? super AudioStream> s) {
            s.onSubscribe(new ByteToAudioEventSubscription(s, inputStream));
        }
    }
}
