# Jarvis v1.0: sherpa-onnx wake word migration

## Decision
Replace Porcupine with sherpa-onnx keyword spotting (KWS). No Picovoice AccessKey.

## Important: not yet integrated
Current v0.9 still uses Porcupine. Do not label an APK as v1.0 or remove its working dependency until the Android native libraries, model files, and Kotlin bindings are verified and CI builds successfully.

## Upstream references
- https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxKws
- https://k2-fsa.github.io/sherpa/onnx/kws/index.html
- https://github.com/k2-fsa/sherpa-onnx/releases/tag/kws-models
- https://k2-fsa.github.io/sherpa/onnx/kws/apk.html

## Implementation checklist
1. Pin a tested sherpa-onnx release, ABI(s), and compatible model license; record checksums.
2. Port the upstream Android KWS sample and bundle model, tokens, and keyword configuration as assets. Check the actual supported pronunciation for 'Jarvis' versus Persian 'جارویس'.
3. Implement a single AudioRecord owner in a microphone foreground service; feed PCM chunks continuously to the KWS engine.
4. On detection, pause and release AudioRecord before starting Android SpeechRecognizer for the user command; resume KWS only after speech recognition/TTS completes and the foreground activity yields microphone ownership.
5. Keep the existing animated overlay; expose an opt-in wake toggle in hamburger > Settings. Remove the Picovoice key field only when KWS is functional.
6. Handle notification, microphone, overlay permissions and Android background restrictions. Do not bypass Android privacy indicators.
7. Validate detection, false positives, battery consumption, lock screen behavior, permission denial and service restart on a physical Android 16 device.
8. Build APK in GitHub Actions; verify success and a downloadable APK before publishing a v1.0 release.

## Acceptance criteria
- No Picovoice dependency, key or network requirement for wake detection.
- No repeated SpeechRecognizer listening loop while idle in background.
- Local wake word detection triggers overlay, then transfers mic ownership to the command recognizer.
- Graceful disable, pause, and notification stop action.
- APK build and release confirmed by GitHub Actions.
