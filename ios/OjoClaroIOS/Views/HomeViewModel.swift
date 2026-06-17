import Foundation

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var spokenText = "Tocá el botón grande y decí: leer texto, describir o pedir ayuda."
    @Published var isLoading = false

    private let parser = CommandParser()
    private let client = AssistantClient()
    private let speech = SpeechOutputService()

    func submit(_ text: String) async {
        isLoading = true

        let command = parser.parse(text)
        let request = AssistRequest(
            command: command,
            userMessage: text,
            imageBase64: nil,
            location: nil,
            deviceLocale: "es-AR",
            accessibilityMode: "VOICE_FIRST"
        )

        do {
            let response = try await client.assist(request)
            spokenText = response.spokenText
            speech.speak(response.spokenText)
        } catch {
            let fallback = "No pude conectar con la IA en la nube. Entendí: \(text). Revisá internet o probá de nuevo."
            spokenText = fallback
            speech.speak(fallback)
        }

        isLoading = false
    }

    func speakCurrentMessage() {
        speech.speak(spokenText)
    }
}
