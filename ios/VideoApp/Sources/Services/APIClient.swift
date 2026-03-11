import Foundation

struct ApiResponse<T: Decodable>: Decodable {
    let code: Int
    let message: String?
    let data: T?
    
    var isSuccess: Bool { code == 0 }
}

enum APIError: LocalizedError {
    case requestFailed(String)
    case decodingFailed
    case noData
    
    var errorDescription: String? {
        switch self {
        case .requestFailed(let msg): return msg
        case .decodingFailed: return "数据解析失败"
        case .noData: return "数据为空"
        }
    }
}

class APIClient {
    static let shared = APIClient()
    private let session = URLSession.shared
    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        return d
    }()
    
    private init() {}
    
    private func makeRequest(path: String, method: String = "GET", body: Data? = nil, baseURL: String? = nil) -> URLRequest {
        let base = baseURL ?? AppConfig.apiBaseURL
        var request = URLRequest(url: URL(string: "\(base)\(path)")!)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let auth = AuthManager.shared
        if let token = auth.token {
            request.setValue(token, forHTTPHeaderField: "token")
        }
        if let clientId = auth.clientId {
            request.setValue(clientId, forHTTPHeaderField: "clientId")
        }
        if let sessionId = auth.sessionId {
            request.setValue(sessionId, forHTTPHeaderField: "sessionId")
        }
        
        request.httpBody = body
        return request
    }
    
    func get<T: Decodable>(_ path: String, baseURL: String? = nil) async throws -> T {
        let request = makeRequest(path: path, baseURL: baseURL)
        let (data, _) = try await session.data(for: request)
        let response = try decoder.decode(ApiResponse<T>.self, from: data)
        guard response.isSuccess else {
            throw APIError.requestFailed(response.message ?? "请求失败")
        }
        guard let result = response.data else {
            throw APIError.noData
        }
        return result
    }
    
    func getOptional<T: Decodable>(_ path: String) async throws -> T? {
        let request = makeRequest(path: path)
        let (data, _) = try await session.data(for: request)
        let response = try decoder.decode(ApiResponse<T?>.self, from: data)
        guard response.isSuccess else {
            throw APIError.requestFailed(response.message ?? "请求失败")
        }
        return response.data ?? nil
    }
    
    func post<T: Decodable>(_ path: String, body: Encodable) async throws -> T {
        let bodyData = try JSONEncoder().encode(body)
        let request = makeRequest(path: path, method: "POST", body: bodyData)
        let (data, _) = try await session.data(for: request)
        let response = try decoder.decode(ApiResponse<T>.self, from: data)
        guard response.isSuccess else {
            throw APIError.requestFailed(response.message ?? "请求失败")
        }
        guard let result = response.data else {
            throw APIError.noData
        }
        return result
    }
    
    func postVoid(_ path: String, body: Encodable) async throws {
        let bodyData = try JSONEncoder().encode(body)
        let request = makeRequest(path: path, method: "POST", body: bodyData)
        let (data, _) = try await session.data(for: request)
        let response = try decoder.decode(ApiResponse<String?>.self, from: data)
        guard response.isSuccess else {
            throw APIError.requestFailed(response.message ?? "请求失败")
        }
    }
    
    func getVoid(_ path: String) async throws {
        let request = makeRequest(path: path)
        let (data, _) = try await session.data(for: request)
        let response = try decoder.decode(ApiResponse<String?>.self, from: data)
        guard response.isSuccess else {
            throw APIError.requestFailed(response.message ?? "请求失败")
        }
    }
}
