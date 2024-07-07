import React, { useState, useEffect, useRef, useCallback } from 'react';
import useWebSocket from 'react-use-websocket';
import VideoPlayer from './VideoPlayer';

const App = () => {
    const [transcription, setTranscription] = useState('');
    const [isTranscribing, setIsTranscribing] = useState(false);
    const socketUrl = 'ws://localhost:8080/audio-stream';
    let { sendMessage, lastMessage, readyState, getWebSocket, setUrl } = useWebSocket(socketUrl);
    const SAMPLE_RATE = 44100;
    const [playersPlayBackUrl, setPlayersPlayBackUrl] = useState('');
    const [playBackUrl, setPlaybackUrl] = useState('');



    useEffect(() => {
        if (lastMessage !== null) {
            setTranscription(parseTranscript(lastMessage.data).Transcript);
        }
    }, [lastMessage]);

    const parseTranscript = (rawOutput) => {
        const transcriptMatch = rawOutput.match(/##Transcript##(.*?)##partial##/);
        const transcript = transcriptMatch ? transcriptMatch[1].trim() : '';

        const partialMatch = rawOutput.match(/##partial##(true|false)/);
        const partial = partialMatch ? (partialMatch[1] === 'true') : false;

        return {
            Transcript: transcript,
            partial: partial
        };
    }

    const setPlayer = () => {
        setPlayersPlayBackUrl('');
        startTranscription(playBackUrl);
        setTimeout(() => {
            setPlayersPlayBackUrl(playBackUrl);
        }, 2000);
    };
      
    const startTranscription = (playBackUrl) => {
        setIsTranscribing(true);
        const message = {
            type: 'startTranscription',
            hlsUrl: playBackUrl
        };
        const binaryMessage = new Blob([JSON.stringify(message)]);
        sendMessage(binaryMessage);
        /*

        try {        
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
            const audioContext = new (window.AudioContext || window.webkitAudioContext)();
            const source = audioContext.createMediaStreamSource(stream);
            const processor = audioContext.createScriptProcessor(4096, 1, 1);
    
            source.connect(processor);
            processor.connect(audioContext.destination);
    
            processor.onaudioprocess = (e) => { 
                const input = e.inputBuffer.getChannelData(0);
                const downsampledBuffer = downsampleBuffer(input, audioContext.sampleRate, 16000);
                const buffer = new Uint8Array(pcmEncode(downsampledBuffer));
                sendMessage(buffer);
            };
            
        } catch (error) {
            console.error('Error accessing the microphone', error);
        }
            */
    };

    const pcmEncode = (input) => {
        var offset = 0;
        var buffer = new ArrayBuffer(input.length * 2);
        var view = new DataView(buffer);
        for (var i = 0; i < input.length; i++, offset += 2) {
            var s = Math.max(-1, Math.min(1, input[i]));
            view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true);
        }
        return buffer;
    };

 

    const downsampleBuffer = (
        buffer,
        inputSampleRate = SAMPLE_RATE,
        outputSampleRate = 16000
    ) => {
        if (outputSampleRate === inputSampleRate) {
            return buffer;
        }

        var sampleRateRatio = inputSampleRate / outputSampleRate;
        var newLength = Math.round(buffer.length / sampleRateRatio);
        var result = new Float32Array(newLength);
        var offsetResult = 0;
        var offsetBuffer = 0;

        while (offsetResult < result.length) {
            var nextOffsetBuffer = Math.round((offsetResult + 1) * sampleRateRatio);

            var accum = 0,
                count = 0;

            for (var i = offsetBuffer; i < nextOffsetBuffer && i < buffer.length; i++) {
                accum += buffer[i];
                count++;
            }

            result[offsetResult] = accum / count;
            offsetResult++;
            offsetBuffer = nextOffsetBuffer;
        }

        return result;
    };




    /*
    const restartWebSocketSession = useCallback(() => {
        const webSocket = getWebSocket();
        if (webSocket) {
            webSocket.close();
        }
        setUrl(null);
        setTimeout(() => setUrl(socketUrl), 1000);
    }, [getWebSocket, setUrl, socketUrl]);
    */

    const stopTranscription = () => {
        setIsTranscribing(false);
        const webSocket = getWebSocket();
        if (webSocket) {
            webSocket.close();
        }
    };

    return (
        <div className="App">
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', margin: '2%' }}>
                <input type="text" style={{ width: '70%' }} onChange={(event) => setPlaybackUrl(event.target.value)} />
                <button style={{ width: '10%', marginRight: '18%' }} onClick={()=>setPlayer()}>Play</button>
            </div>
            <div style={{paddingLeft: '10%', paddingRight: '10%'}}>
                <VideoPlayer manifestUrl={playersPlayBackUrl} />
            </div>
            <div style={{ margin: '2%' }}>
                <p>{transcription.toString()}</p>
            </div>
        </div>
    );
}

export default App;
