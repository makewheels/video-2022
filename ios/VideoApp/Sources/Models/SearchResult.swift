import Foundation

struct SearchResultResponse: Decodable {
    let content: [VideoItem]
    let total: Int
    let totalPages: Int
    let currentPage: Int
    let pageSize: Int
}
