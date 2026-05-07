import Foundation

final class CommandParser {
    func parse(_ input: String, locale: String = "es-AR") -> AppCommand {
        let normalized = input.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)

        if contains(normalized, ["leer", "leeme", "texto", "cartel", "ticket", "boleta", "factura"]) {
            return AppCommand(type: .readText, originalText: input, locale: locale, requiresCamera: true, requiresLocation: false, highRisk: false)
        }

        if contains(normalized, ["describir", "qué ves", "que ves", "qué tengo enfrente", "que tengo enfrente"]) {
            return AppCommand(type: .describeScene, originalText: input, locale: locale, requiresCamera: true, requiresLocation: false, highRisk: false)
        }

        if contains(normalized, ["producto", "precio", "vencimiento", "ingredientes"]) {
            return AppCommand(type: .identifyProduct, originalText: input, locale: locale, requiresCamera: true, requiresLocation: false, highRisk: false)
        }

        if contains(normalized, ["ayuda", "emergencia", "perdido", "perdida", "auxilio"]) {
            return AppCommand(type: .emergencyHelp, originalText: input, locale: locale, requiresCamera: false, requiresLocation: true, highRisk: true)
        }

        return AppCommand(type: .unknown, originalText: input, locale: locale, requiresCamera: false, requiresLocation: false, highRisk: false)
    }

    private func contains(_ text: String, _ tokens: [String]) -> Bool {
        tokens.contains { text.contains($0) }
    }
}
