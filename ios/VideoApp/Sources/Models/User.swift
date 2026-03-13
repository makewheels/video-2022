import Foundation

struct LoginResponse: Decodable {
    let token: String?
    let sessionId: String?
    let clientId: String?
    let nickname: String?
    let avatarUrl: String?
    let bannerUrl: String?
    let bio: String?
}
