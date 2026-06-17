import Foundation

enum AppCommandType: String, Codable {
    case describeScene = "DESCRIBE_SCENE"
    case readText = "READ_TEXT"
    case readDocument = "READ_DOCUMENT"
    case identifyProduct = "IDENTIFY_PRODUCT"
    case emergencyHelp = "EMERGENCY_HELP"
    case currentLocation = "CURRENT_LOCATION"
    case unknown = "UNKNOWN"
}

struct AppCommand: Codable {
    let type: AppCommandType
    let originalText: String
    let locale: String
    let requiresCamera: Bool
    let requiresLocation: Bool
    let highRisk: Bool
}

struct AssistRequest: Codable {
    let command: AppCommand
    let userMessage: String
    let imageBase64: String?
    let location: GeoPoint?
    let deviceLocale: String
    let accessibilityMode: String
}

struct GeoPoint: Codable {
    let latitude: Double
    let longitude: Double
    let accuracyMeters: Double?
}

struct AssistResponse: Codable {
    let spokenText: String
    let shortText: String
    let confidence: String
    let category: String
    let safetyNotice: String?
    let suggestedActions: [SuggestedAction]
}

struct SuggestedAction: Codable {
    let id: String
    let label: String
    let commandType: AppCommandType
}
