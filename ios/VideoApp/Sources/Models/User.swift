import Foundation

struct LoginResponse: Decodable {
    let token: String?
    let sessionId: String?
    let clientId: String?
}
