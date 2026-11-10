# Jarvis v1.0 — sherpa-onnx integration status

## Source changes completed
- Offline keyword detection implementation: `SherpaWakeEngine.kt` (sherpa-onnx Kotlin API).
- 16-kHz continuous PCM microphone capture: `WakeAudioCapture.kt`.
- Background microphone foreground service: `WakeService.kt`; continuous KWS replaces cycling `SpeechRecognizer`.
- New settings require no Picovoice AccessKey; enable wake word and grant Android microphone + overlay permissions.
- Animated wake overlay retained. **Tap the overlay** to activate Android Persian command recognition.
- When a voice session ends, microphone ownership returns to the offline wake detector.
- Stop notification action disables wake mode in preferences.

## Build pipeline
`scripts/prepare-sherpa.sh` downloads the official `sherpa-onnx-2.0.1.aar`
(sha256: `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`)
and English GigaSpeech KWS model `sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01`.
The script generates a tokenized `JARVIS` keyword using the model's SentencePiece vocabulary
and bundles the ONNX files as Android assets. GitHub Actions builds an arm64 debug APK,
and publishes `JarvisAI-v1.0.apk` to Releases **only if the build passes**.

## Testing not yet verified
- GitHub Actions build success and Release APK availability must be confirmed.
- A physical Android device must validate wake detection with the user's accent
  (English-trained model may miss Persian pronunciation), false positives and
  CPU/battery performance.
- Android's microphone privacy indicator remains visible while listening;
  the goal is to eliminate periodic SpeechRecognizer session restarts, not
  conceal microphone use.
- This is an experimental one-shot interaction: tap the overlay, speak a Persian
  command, hear the response, return to offline wake monitoring.
- The command recognition stage may use the device's Google speech service;
  **only wake-word detection is guaranteed to be local**.

## References
- https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxKws
- https://k2-fsa.github.io/sherpa/onnx/kws/index.html
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models
