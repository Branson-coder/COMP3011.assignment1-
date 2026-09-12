(() => {
    "use strict";

    const recordBtn = document.getElementById("recordBtn");
    const recordLabel = document.getElementById("recordLabel");
    const statusLine = document.getElementById("statusLine");
    const transcriptionText = document.getElementById("transcriptionText");

    /** @type {MediaRecorder|null} */
    let mediaRecorder = null;
    /** @type {Blob[]} */
    let chunks = [];
    /** @type {MediaStream|null} */
    let activeStream = null;
    let isRecording = false;
    let recordingStartedAt = 0;
    let elapsedTimerId = null;

    // Compression: prefer Opus in a WebM container at a low, speech-appropriate
    // bitrate. Opus at 24kbps is well above what's needed for clear speech
    // (voice codecs are usable well below music bitrates) and meaningfully
    // cuts upload size/time versus the browser's uncompressed or high-bitrate
    // default, which matters for the "within 5 seconds" requirement on
    // slower connections. We probe supported mime types in preference order
    // since not every browser supports every codec/container combination.
    const PREFERRED_MIME_TYPES = [
        "audio/webm;codecs=opus",
        "audio/ogg;codecs=opus",
        "audio/webm",
        "audio/ogg"
    ];
    const TARGET_AUDIO_BITRATE = 24000; // bits per second - see note above

    function pickSupportedMimeType() {
        if (typeof MediaRecorder === "undefined" || !MediaRecorder.isTypeSupported) {
            return null; // let the browser pick its own default
        }
        return PREFERRED_MIME_TYPES.find((type) => MediaRecorder.isTypeSupported(type)) || null;
    }

    function setStatus(message, isError = false) {
        statusLine.textContent = message;
        statusLine.classList.toggle("error", isError);
    }

    function setRecordingUi(recording) {
        isRecording = recording;
        recordBtn.classList.toggle("recording", recording);
        recordBtn.setAttribute("aria-pressed", String(recording));
        recordLabel.textContent = recording ? "Stop Recording" : "Start Recording";
    }

    function startElapsedTimer() {
        recordingStartedAt = performance.now();
        elapsedTimerId = window.setInterval(() => {
            const seconds = ((performance.now() - recordingStartedAt) / 1000).toFixed(1);
            setStatus(`Recording... speak now. (${seconds}s)`);
        }, 200);
    }

    function stopElapsedTimer() {
        if (elapsedTimerId !== null) {
            window.clearInterval(elapsedTimerId);
            elapsedTimerId = null;
        }
    }

    async function startRecording() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            setStatus("This browser does not support microphone access.", true);
            return;
        }

        try {
            activeStream = await navigator.mediaDevices.getUserMedia({ audio: true });
        } catch (err) {
            // Distinguish the common cases so the user knows what to actually do,
            // rather than one generic "denied or unavailable" message for everything.
            if (err && err.name === "NotAllowedError") {
                setStatus("Microphone access was denied. Allow it in your browser's site settings, then try again.", true);
            } else if (err && err.name === "NotFoundError") {
                setStatus("No microphone was found on this device.", true);
            } else {
                setStatus("Could not access the microphone. Try again.", true);
            }
            return;
        }

        chunks = [];
        const mimeType = pickSupportedMimeType();
        try {
            mediaRecorder = mimeType
                ? new MediaRecorder(activeStream, { mimeType, audioBitsPerSecond: TARGET_AUDIO_BITRATE })
                : new MediaRecorder(activeStream);
        } catch (err) {
            setStatus("Recording is not supported in this browser.", true);
            stopStream();
            return;
        }

        mediaRecorder.addEventListener("dataavailable", (event) => {
            if (event.data && event.data.size > 0) {
                chunks.push(event.data);
            }
        });

        mediaRecorder.addEventListener("stop", handleRecordingStopped);

        mediaRecorder.start();
        setRecordingUi(true);
        startElapsedTimer();
    }

    function stopRecording() {
        stopElapsedTimer();
        if (mediaRecorder && mediaRecorder.state !== "inactive") {
            mediaRecorder.stop();
        }
        setRecordingUi(false);
    }

    function stopStream() {
        if (activeStream) {
            activeStream.getTracks().forEach((track) => track.stop());
            activeStream = null;
        }
    }

    async function handleRecordingStopped() {
        stopStream();
        setStatus("Transcribing...");
        recordBtn.disabled = true;

        const mimeType = mediaRecorder && mediaRecorder.mimeType ? mediaRecorder.mimeType : "audio/webm";
        const blob = new Blob(chunks, { type: mimeType });

        if (blob.size === 0) {
            setStatus("No audio was captured - try again.", true);
            recordBtn.disabled = false;
            return;
        }

        const extension = mimeType.includes("ogg") ? "ogg" : mimeType.includes("wav") ? "wav" : "webm";
        const formData = new FormData();
        formData.append("audio", blob, `recording.${extension}`);

        try {
            const uploadStarted = performance.now();
            const response = await fetch("/api/v1/transcribe", {
                method: "POST",
                body: formData
            });
            const uploadMillis = Math.round(performance.now() - uploadStarted);

            if (!response.ok) {
                let message = `Server returned HTTP ${response.status}`;
                try {
                    const errorBody = await response.json();
                    if (errorBody && errorBody.message) {
                        message = errorBody.message;
                    }
                } catch (_) {
                    // response body wasn't JSON - fall back to the generic message
                }
                setStatus(message, true);
                transcriptionText.textContent = "Nothing yet - record something to get started.";
                return;
            }

            const result = await response.json();
            transcriptionText.textContent = result.text && result.text.trim().length > 0
                ? result.text
                : "(No speech detected)";
            setStatus(`Ready. (${(blob.size / 1024).toFixed(1)}KB uploaded in ${uploadMillis}ms)`);
        } catch (err) {
            setStatus("Network error while uploading audio. Check your connection and try again.", true);
        } finally {
            recordBtn.disabled = false;
        }
    }

    recordBtn.addEventListener("click", () => {
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    });
})();
