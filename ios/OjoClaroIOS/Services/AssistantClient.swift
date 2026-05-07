import Foundation

final class AssistantClient {
    private let baseURL: URL
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()
    private let session: URLSession

    init(baseURL: URL = URL(string: "http://localhost:8080")!, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    func assist(_ request: AssistRequest) async throws -> AssistResponse {
        guard let endpoint = URL(string: "api/v1/assist", relativeTo: baseURL)?.absoluteURL else {
            throw AssistantClientError.invalidURL
        }

        var urlRequest = URLRequest(url: endpoint)
        urlRequest.httpMethod = "POST"
        urlRequest.timeoutInterval = 20
        urlRequest.addValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = try encoder.encode(request)

        let (data, response) = try await session.data(for: urlRequest)

        guard let httpResponse = response as? HTTPURLResponse,
              (200..<300).contains(httpResponse.statusCode) else {
            throw AssistantClientError.invalidResponse
        }

        return try decoder.decode(AssistResponse.self, from: data)
    }
}

enum AssistantClientError: Error {
    case invalidURL
    case invalidResponse
}
