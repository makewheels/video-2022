import Foundation

struct WatchInfo: Decodable {
    let videoId: String
    let coverUrl: String?
    let videoStatus: String?
    let multivariantPlaylistUrl: String?
    let progressInMillis: Int64?
}

struct HeartbeatRequest: Encodable {
    let videoId: String
    let clientId: String
    let sessionId: String
    let videoStatus: String
    let playerProvider: String
    let clientTime: String
    let type: String
    let event: String?
    let playerTime: Int64
    let playerStatus: String
    let playerVolume: Float
}

struct ProgressResponse: Decodable {
    let progressInMillis: Int64?
}
