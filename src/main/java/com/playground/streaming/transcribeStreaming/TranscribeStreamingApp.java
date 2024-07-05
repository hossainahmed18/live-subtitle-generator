package com.playground.streaming.transcribeStreaming;

import javafx.application.Application;
import javafx.stage.Stage;

public class TranscribeStreamingApp extends Application {

    @Override
    public void start(Stage primaryStage)  {

        WindowController windowController = new WindowController(primaryStage);

        primaryStage.setOnCloseRequest(__ -> {
            windowController.close();
            System.exit(0);
        });
        primaryStage.show();

    }

    public static void main(String args[]) {
        launch(args);
    }

}