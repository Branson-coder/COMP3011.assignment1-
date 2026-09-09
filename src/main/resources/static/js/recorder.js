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

    async function startRecording() {
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            setStatus("This browser does not support microphone access.", true);
            return;
        }

        try {
            activeStream = await navigator.mediaDevices.getUserMedia({ audio: true });
        } catch (err) {
            setStatus("Microphone access was denied or unavailable.", true);
            return;
        }

        chunks = [];
        try {
            mediaRecorder = new MediaRecorder(activeStream);
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
        setStatus("Recording... speak now.");
    }

    function stopRecording() {
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
            const response = await fetch("/api/v1/transcribe", {
                method: "POST",
                body: formData
            });

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
            setStatus("Ready.");
        } catch (err) {
            setStatus("Network error while uploading audio.", true);
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
