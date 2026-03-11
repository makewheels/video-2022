import Foundation

struct Comment: Decodable, Identifiable {
    let id: String
    let videoId: String?
    let userId: String?
    let userPhone: String?
    let content: String?
    let parentId: String?
    let replyToUserId: String?
    let replyToUserPhone: String?
    let likeCount: Int?
    let replyCount: Int?
    let createTime: String?
    let updateTime: String?
}

struct AddCommentRequest: Encodable {
    let videoId: String
    let content: String
    let parentId: String?
}

struct CommentCount: Decodable {
    let count: Int
}
