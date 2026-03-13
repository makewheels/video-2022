import XCTest
@testable import VideoApp

final class VideoModelTests: XCTestCase {

    // MARK: - VideoItem Decoding

    func testVideoItemDecodingAllFields() throws {
        let json = """
        {
            "id": "vid-001",
            "watchId": "watch-abc",
            "title": "测试视频",
            "description": "这是一段测试描述",
            "status": "READY",
            "visibility": "PUBLIC",
            "watchCount": 1024,
            "duration": 360,
            "createTime": "2024-06-15T10:30:00Z",
            "createTimeString": "2024-06-15",
            "watchUrl": "https://example.com/watch/abc",
            "shortUrl": "https://short.url/abc",
            "type": "USER_UPLOAD",
            "coverUrl": "https://example.com/cover.jpg",
            "youtubePublishTimeString": "2024-06-14",
            "uploaderName": "TestUser",
            "uploaderAvatarUrl": "https://example.com/avatar.jpg",
            "uploaderId": "user-123"
        }
        """.data(using: .utf8)!

        let video = try JSONDecoder().decode(VideoItem.self, from: json)
        XCTAssertEqual(video.id, "vid-001")
        XCTAssertEqual(video.watchId, "watch-abc")
        XCTAssertEqual(video.title, "测试视频")
        XCTAssertEqual(video.description, "这是一段测试描述")
        XCTAssertEqual(video.status, "READY")
        XCTAssertEqual(video.visibility, "PUBLIC")
        XCTAssertEqual(video.watchCount, 1024)
        XCTAssertEqual(video.duration, 360)
        XCTAssertEqual(video.createTime, "2024-06-15T10:30:00Z")
        XCTAssertEqual(video.createTimeString, "2024-06-15")
        XCTAssertEqual(video.watchUrl, "https://example.com/watch/abc")
        XCTAssertEqual(video.shortUrl, "https://short.url/abc")
        XCTAssertEqual(video.type, "USER_UPLOAD")
        XCTAssertEqual(video.coverUrl, "https://example.com/cover.jpg")
        XCTAssertEqual(video.youtubePublishTimeString, "2024-06-14")
        XCTAssertEqual(video.uploaderName, "TestUser")
        XCTAssertEqual(video.uploaderAvatarUrl, "https://example.com/avatar.jpg")
        XCTAssertEqual(video.uploaderId, "user-123")
    }

    func testVideoItemDecodingMinimalFields() throws {
        let json = """
        { "id": "vid-minimal" }
        """.data(using: .utf8)!

        let video = try JSONDecoder().decode(VideoItem.self, from: json)
        XCTAssertEqual(video.id, "vid-minimal")
        XCTAssertNil(video.watchId)
        XCTAssertNil(video.title)
        XCTAssertNil(video.description)
        XCTAssertNil(video.watchCount)
        XCTAssertNil(video.duration)
        XCTAssertNil(video.coverUrl)
        XCTAssertNil(video.uploaderName)
    }

    func testVideoItemIdentifiable() throws {
        let json = """
        { "id": "unique-id" }
        """.data(using: .utf8)!

        let video = try JSONDecoder().decode(VideoItem.self, from: json)
        XCTAssertEqual(video.id, "unique-id")
    }

    // MARK: - VideoListResponse Decoding

    func testVideoListResponseDecoding() throws {
        let json = """
        {
            "list": [
                { "id": "v1", "title": "First" },
                { "id": "v2", "title": "Second" }
            ],
            "total": 42
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(VideoListResponse.self, from: json)
        XCTAssertEqual(response.total, 42)
        XCTAssertEqual(response.list.count, 2)
        XCTAssertEqual(response.list[0].id, "v1")
        XCTAssertEqual(response.list[0].title, "First")
        XCTAssertEqual(response.list[1].id, "v2")
    }

    func testVideoListResponseEmptyList() throws {
        let json = """
        { "list": [], "total": 0 }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(VideoListResponse.self, from: json)
        XCTAssertEqual(response.total, 0)
        XCTAssertTrue(response.list.isEmpty)
    }

    // MARK: - CreateVideoRequest Encoding

    func testCreateVideoRequestEncodingDefaults() throws {
        let request = CreateVideoRequest(videoType: "USER_UPLOAD", rawFilename: "test.mp4", size: 1048576)
        let data = try JSONEncoder().encode(request)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["videoType"] as? String, "USER_UPLOAD")
        XCTAssertEqual(dict["rawFilename"] as? String, "test.mp4")
        XCTAssertEqual(dict["size"] as? Int64, 1048576)
        XCTAssertEqual(dict["ttl"] as? String, "PERMANENT")
        XCTAssertNil(dict["youtubeUrl"])
    }

    func testCreateVideoRequestEncodingYoutube() throws {
        let request = CreateVideoRequest(videoType: "YOUTUBE", youtubeUrl: "https://youtube.com/watch?v=abc")
        let data = try JSONEncoder().encode(request)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["videoType"] as? String, "YOUTUBE")
        XCTAssertEqual(dict["youtubeUrl"] as? String, "https://youtube.com/watch?v=abc")
        XCTAssertNil(dict["rawFilename"])
        XCTAssertNil(dict["size"])
    }

    // MARK: - CreateVideoResponse Decoding

    func testCreateVideoResponseDecoding() throws {
        let json = """
        {
            "watchId": "w-123",
            "shortUrl": "https://short.url/w",
            "videoId": "vid-abc",
            "watchUrl": "https://example.com/watch/w-123",
            "fileId": "file-xyz"
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(CreateVideoResponse.self, from: json)
        XCTAssertEqual(response.watchId, "w-123")
        XCTAssertEqual(response.shortUrl, "https://short.url/w")
        XCTAssertEqual(response.videoId, "vid-abc")
        XCTAssertEqual(response.watchUrl, "https://example.com/watch/w-123")
        XCTAssertEqual(response.fileId, "file-xyz")
    }

    // MARK: - UpdateVideoInfoRequest Encoding

    func testUpdateVideoInfoRequestEncoding() throws {
        let request = UpdateVideoInfoRequest(id: "vid-1", title: "New Title", description: nil, visibility: "PRIVATE")
        let data = try JSONEncoder().encode(request)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["id"] as? String, "vid-1")
        XCTAssertEqual(dict["title"] as? String, "New Title")
        XCTAssertEqual(dict["visibility"] as? String, "PRIVATE")
    }
}
