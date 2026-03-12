import Foundation

struct Playlist: Decodable, Identifiable {
    let id: String
    let title: String?
    let description: String?
    let ownerId: String?
    let visibility: String?
    let deleted: Bool?
    let createTime: String?
    let updateTime: String?
}

struct PlaylistItem: Decodable, Identifiable {
    var id: String { videoId ?? UUID().uuidString }
    let videoId: String?
    let watchId: String?
    let title: String?
    let coverUrl: String?
    let watchCount: Int?
    let videoCreateTime: String?
}

struct CreatePlaylistRequest: Encodable {
    let title: String
    let description: String?
}

struct DeletePlaylistItemRequest: Encodable {
    let playlistId: String
    let deleteMode: String
    let videoIdList: [String]
}
