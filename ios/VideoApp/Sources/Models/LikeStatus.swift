import Foundation

struct LikeStatus: Decodable {
    let likeCount: Int
    let dislikeCount: Int
    let userAction: String?
}
