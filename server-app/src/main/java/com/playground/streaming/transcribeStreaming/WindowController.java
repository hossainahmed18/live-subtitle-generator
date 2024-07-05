
package com.playground.streaming.transcribeStreaming;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponse;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptResultStream;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class WindowController {

    private TranscribeStreamingClientWrapper client;
    private TextArea outputTextArea;
    private Button startStopMicButton;
    private Button fileStreamButton;
    private Button saveButton;
    private TextArea finalTextArea;
    private CompletableFuture<Void> inProgressStreamingRequest;
    private String finalTranscript = "";
    private Stage primaryStage;

    public WindowController(Stage primaryStage) {
        client = new TranscribeStreamingClientWrapper();
        this.primaryStage = primaryStage;
        initializeWindow(primaryStage);
    }

    public void close() {
        if (inProgressStreamingRequest != null) {
            inProgressStreamingRequest.completeExceptionally(new InterruptedException());
        }
        client.close();
    }

    private void startTranscriptionRequest(String inputFile) {
        if (inProgressStreamingRequest == null) {
            finalTextArea.clear();
            finalTranscript = "";
            startStopMicButton.setText("Connecting...");
            startStopMicButton.setDisable(true);
            outputTextArea.clear();
            finalTextArea.clear();
            saveButton.setDisable(true);
            inProgressStreamingRequest = client.startTranscription(getResponseHandlerForWindow(), inputFile);
        }
    }

    private void initializeWindow(Stage primaryStage) {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        grid.setVgap(10);
        grid.setHgap(10);
        grid.setPadding(new Insets(25, 25, 25, 25));

        Scene scene = new Scene(grid, 500, 600);
        primaryStage.setScene(scene);

        startStopMicButton = new Button();
        startStopMicButton.setText("Start Microphone Transcription");
        startStopMicButton.setOnAction(__ -> startTranscriptionRequest(null));
        grid.add(startStopMicButton, 0, 0, 1, 1);

        fileStreamButton = new Button();
        fileStreamButton.setText("Stream From Audio File"); 
        fileStreamButton.setOnAction(__ -> startTranscriptionRequest("https://vimond.video-output.eu-north-1-dev.vmnd.tv/fbd642b6-8e5d-4e2b-8f07-efe3ee98ce26/hls/sample_news_976149.m3u8"));
        grid.add(fileStreamButton, 1, 0, 1, 1);

        Text inProgressText = new Text("In Progress Transcriptions:");
        grid.add(inProgressText, 0, 1, 2, 1);

        outputTextArea = new TextArea();
        outputTextArea.setWrapText(true);
        outputTextArea.setEditable(false);
        grid.add(outputTextArea, 0, 2, 2, 1);

        Text finalText = new Text("Final Transcription:");
        grid.add(finalText, 0, 3, 2, 1);

        finalTextArea = new TextArea();
        finalTextArea.setWrapText(true);
        finalTextArea.setEditable(false);
        grid.add(finalTextArea, 0, 4, 2, 1);

        saveButton = new Button();
        saveButton.setDisable(true);
        saveButton.setText("Save Full Transcript");
        grid.add(saveButton, 0, 5, 2, 1);


    }

    private void stopTranscription() {
        if (inProgressStreamingRequest != null) {
            try {
                saveButton.setDisable(true);
                client.stopTranscription();
                inProgressStreamingRequest.get();
            } catch (ExecutionException | InterruptedException e) {
                System.out.println("error closing stream");
            } finally {
                inProgressStreamingRequest = null;
                startStopMicButton.setText("Start Microphone Transcription");
                startStopMicButton.setOnAction(__ -> startTranscriptionRequest(null));
                startStopMicButton.setDisable(false);
            }

        }
    }

 
    private StreamTranscriptionBehavior getResponseHandlerForWindow() {
        return new StreamTranscriptionBehavior() {

            @Override
            public void onError(Throwable e) {
                System.out.println(e.getMessage());
                Throwable cause = e.getCause();
                while (cause != null) {
                    System.out.println("Caused by: " + cause.getMessage());
                    Arrays.stream(cause.getStackTrace()).forEach(l -> System.out.println("  " + l));
                    if (cause.getCause() != cause) { //Look out for circular causes
                        cause = cause.getCause();
                    } else {
                        cause = null;
                    }
                }
                System.out.println("Error Occurred: " + e);
            }

        
            @Override
            public void onStream(TranscriptResultStream event) {
                List<Result> results = ((TranscriptEvent) event).transcript().results();
                if(results.size()>0) {
                    Result firstResult = results.get(0);
                    if (firstResult.alternatives().size() > 0 && !firstResult.alternatives().get(0).transcript().isEmpty()) {
                        String transcript = firstResult.alternatives().get(0).transcript();
                        if(!transcript.isEmpty()) {
                           
                            String displayText;
                            if (!firstResult.isPartial()) {
                                finalTranscript += transcript + " ";
                                displayText = finalTranscript;
                                System.out.println("final");
                            } else {
                                displayText = finalTranscript + " " + transcript;
                            }
                            System.out.println(displayText);
                   
                            Platform.runLater(() -> {
                                outputTextArea.setText(displayText);
                                outputTextArea.setScrollTop(Double.MAX_VALUE);
                            });
                        }
                    }

                }
            }

        
            @Override
            public void onResponse(StartStreamTranscriptionResponse r) {
                System.out.println(String.format("=== Received Initial response. Request Id: %s ===", r.requestId()));
                Platform.runLater(() -> {
                    startStopMicButton.setText("Stop Transcription");
                    startStopMicButton.setOnAction(__ -> stopTranscription());
                    startStopMicButton.setDisable(false);
                });
            }

            @Override
            public void onComplete() {
                System.out.println("=== All records streamed successfully ===");
                Platform.runLater(() -> {
                    finalTextArea.setText(finalTranscript);
                    saveButton.setDisable(false);
                    saveButton.setOnAction(__ -> {
                        FileChooser fileChooser = new FileChooser();
                        fileChooser.setTitle("Save Transcript");
                        File file = fileChooser.showSaveDialog(primaryStage);
                        if (file != null) {
                            try {
                                FileWriter writer = new FileWriter(file);
                                writer.write(finalTranscript);
                                writer.close();
                            } catch (IOException e) {
                                System.out.println("Error saving transcript to file: " + e);
                            }
                        }
                    });

                });
            }
        };
    }

}
