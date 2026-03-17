import Foundation

struct WatchHistoryItem: Decodable, Identifiable {
    let videoId: String
    let title: String?
    let coverUrl: String?
    let watchTime: String?

    var id: String { videoId + (watchTime ?? "") }
}

struct WatchHistoryResponse: Decodable {
    let list: [WatchHistoryItem]
    let total: Int
    let page: Int
    let pageSize: Int
}
