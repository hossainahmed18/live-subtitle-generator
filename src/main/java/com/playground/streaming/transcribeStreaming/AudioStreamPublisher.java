

package com.playground.streaming.transcribeStreaming;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;

import java.io.InputStream;

public class AudioStreamPublisher implements Publisher<AudioStream> {

    private final InputStream inputStream;

    public AudioStreamPublisher(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public void subscribe(Subscriber<? super AudioStream> s) {
        s.onSubscribe(new ByteToAudioEventSubscription(s, inputStream));
    }
}
