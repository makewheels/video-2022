import Foundation

struct NotificationItem: Decodable, Identifiable {
    let id: String
    let type: String?
    let content: String?
    let fromUserId: String?
    let toUserId: String?
    let relatedVideoId: String?
    let relatedCommentId: String?
    let read: Bool
    let createTime: String?
}

struct NotificationListResponse: Decodable {
    let list: [NotificationItem]
    let total: Int
    let totalPages: Int
    let currentPage: Int
    let pageSize: Int
}

struct MarkAsReadRequest: Encodable {
    let notificationId: String
}
