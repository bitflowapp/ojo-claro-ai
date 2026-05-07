import AVFoundation

@MainActor
final class SpeechOutputService {
    private let synthesizer = AVSpeechSynthesizer()

    func speak(_ text: String) {
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "es-AR")
        utterance.rate = 0.48
        utterance.pitchMultiplier = 1.0

        synthesizer.stopSpeaking(at: .immediate)
        synthesizer.speak(utterance)
    }
}
