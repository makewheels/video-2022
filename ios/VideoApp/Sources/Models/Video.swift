import Foundation

struct VideoItem: Decodable, Identifiable {
    let id: String
    let watchId: String?
    let title: String?
    let description: String?
    let status: String?
    let visibility: String?
    let watchCount: Int?
    let duration: Int?
    let createTime: String?
    let createTimeString: String?
    let watchUrl: String?
    let shortUrl: String?
    let type: String?
    let coverUrl: String?
    let youtubePublishTimeString: String?
    let uploaderName: String?
    let uploaderAvatarUrl: String?
    let uploaderId: String?
    let tags: [String]?
    let category: String?
}

struct VideoListResponse: Decodable {
    let list: [VideoItem]
    let total: Int
}

struct CreateVideoRequest: Encodable {
    let videoType: String
    let rawFilename: String?
    let youtubeUrl: String?
    let size: Int64?
    let ttl: String
    
    init(videoType: String, rawFilename: String? = nil, youtubeUrl: String? = nil, size: Int64? = nil, ttl: String = "PERMANENT") {
        self.videoType = videoType
        self.rawFilename = rawFilename
        self.youtubeUrl = youtubeUrl
        self.size = size
        self.ttl = ttl
    }
}

struct CreateVideoResponse: Decodable {
    let watchId: String
    let shortUrl: String
    let videoId: String
    let watchUrl: String
    let fileId: String
}

struct UpdateVideoInfoRequest: Encodable {
    let id: String
    let title: String?
    let description: String?
    let visibility: String?
    var tags: [String]?
    var category: String?
}
