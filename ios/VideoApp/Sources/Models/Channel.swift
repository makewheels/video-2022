import Foundation

struct ChannelInfo: Decodable {
    let userId: String
    let nickname: String?
    let avatarUrl: String?
    let bannerUrl: String?
    let bio: String?
    let subscriberCount: Int
    let videoCount: Int
    var isSubscribed: Bool
}

struct UpdateProfileRequest: Encodable {
    let nickname: String?
    let bio: String?
}
