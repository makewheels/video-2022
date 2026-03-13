import XCTest
@testable import VideoApp

final class CommentModelTests: XCTestCase {

    // MARK: - Comment Decoding

    func testCommentDecodingAllFields() throws {
        let json = """
        {
            "id": "comment-001",
            "videoId": "vid-abc",
            "userId": "user-123",
            "userPhone": "13800138000",
            "content": "这是一条测试评论",
            "parentId": "comment-parent",
            "replyToUserId": "user-456",
            "replyToUserPhone": "13900139000",
            "likeCount": 10,
            "replyCount": 3,
            "createTime": "2024-06-15T10:00:00Z",
            "updateTime": "2024-06-15T12:00:00Z"
        }
        """.data(using: .utf8)!

        let comment = try JSONDecoder().decode(Comment.self, from: json)
        XCTAssertEqual(comment.id, "comment-001")
        XCTAssertEqual(comment.videoId, "vid-abc")
        XCTAssertEqual(comment.userId, "user-123")
        XCTAssertEqual(comment.userPhone, "13800138000")
        XCTAssertEqual(comment.content, "这是一条测试评论")
        XCTAssertEqual(comment.parentId, "comment-parent")
        XCTAssertEqual(comment.replyToUserId, "user-456")
        XCTAssertEqual(comment.replyToUserPhone, "13900139000")
        XCTAssertEqual(comment.likeCount, 10)
        XCTAssertEqual(comment.replyCount, 3)
        XCTAssertEqual(comment.createTime, "2024-06-15T10:00:00Z")
        XCTAssertEqual(comment.updateTime, "2024-06-15T12:00:00Z")
    }

    func testCommentDecodingMinimalFields() throws {
        let json = """
        { "id": "comment-min" }
        """.data(using: .utf8)!

        let comment = try JSONDecoder().decode(Comment.self, from: json)
        XCTAssertEqual(comment.id, "comment-min")
        XCTAssertNil(comment.videoId)
        XCTAssertNil(comment.userId)
        XCTAssertNil(comment.content)
        XCTAssertNil(comment.parentId)
        XCTAssertNil(comment.likeCount)
        XCTAssertNil(comment.replyCount)
    }

    func testCommentIdentifiable() throws {
        let json = """
        { "id": "identifiable-id" }
        """.data(using: .utf8)!

        let comment = try JSONDecoder().decode(Comment.self, from: json)
        XCTAssertEqual(comment.id, "identifiable-id")
    }

    // MARK: - AddCommentRequest Encoding

    func testAddCommentRequestEncoding() throws {
        let request = AddCommentRequest(videoId: "vid-1", content: "Great video!", parentId: nil)
        let data = try JSONEncoder().encode(request)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["videoId"] as? String, "vid-1")
        XCTAssertEqual(dict["content"] as? String, "Great video!")
    }

    func testAddCommentRequestEncodingWithParent() throws {
        let request = AddCommentRequest(videoId: "vid-1", content: "Reply!", parentId: "parent-comment-id")
        let data = try JSONEncoder().encode(request)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["videoId"] as? String, "vid-1")
        XCTAssertEqual(dict["content"] as? String, "Reply!")
        XCTAssertEqual(dict["parentId"] as? String, "parent-comment-id")
    }

    // MARK: - CommentCount Decoding

    func testCommentCountDecoding() throws {
        let json = """
        { "count": 99 }
        """.data(using: .utf8)!

        let result = try JSONDecoder().decode(CommentCount.self, from: json)
        XCTAssertEqual(result.count, 99)
    }

    func testCommentCountDecodingZero() throws {
        let json = """
        { "count": 0 }
        """.data(using: .utf8)!

        let result = try JSONDecoder().decode(CommentCount.self, from: json)
        XCTAssertEqual(result.count, 0)
    }

    // MARK: - Playlist Model Tests

    func testPlaylistDecoding() throws {
        let json = """
        {
            "id": "pl-001",
            "title": "My Playlist",
            "description": "A test playlist",
            "ownerId": "user-abc",
            "visibility": "PUBLIC",
            "deleted": false,
            "createTime": "2024-01-01T00:00:00Z",
            "updateTime": "2024-06-01T00:00:00Z"
        }
        """.data(using: .utf8)!

        let playlist = try JSONDecoder().decode(Playlist.self, from: json)
        XCTAssertEqual(playlist.id, "pl-001")
        XCTAssertEqual(playlist.title, "My Playlist")
        XCTAssertEqual(playlist.description, "A test playlist")
        XCTAssertEqual(playlist.ownerId, "user-abc")
        XCTAssertEqual(playlist.visibility, "PUBLIC")
        XCTAssertEqual(playlist.deleted, false)
    }

    func testPlaylistDecodingMinimal() throws {
        let json = """
        { "id": "pl-min" }
        """.data(using: .utf8)!

        let playlist = try JSONDecoder().decode(Playlist.self, from: json)
        XCTAssertEqual(playlist.id, "pl-min")
        XCTAssertNil(playlist.title)
        XCTAssertNil(playlist.ownerId)
        XCTAssertNil(playlist.deleted)
    }

    func testCreatePlaylistRequestEncoding() throws {
        let request = CreatePlaylistRequest(title: "New PL", description: "Desc")
        let data = try JSONEncoder().encode(request)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["title"] as? String, "New PL")
        XCTAssertEqual(dict["description"] as? String, "Desc")
    }

    func testDeletePlaylistItemRequestEncoding() throws {
        let request = DeletePlaylistItemRequest(playlistId: "pl-1", deleteMode: "BATCH", videoIdList: ["v1", "v2"])
        let data = try JSONEncoder().encode(request)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["playlistId"] as? String, "pl-1")
        XCTAssertEqual(dict["deleteMode"] as? String, "BATCH")
        XCTAssertEqual(dict["videoIdList"] as? [String], ["v1", "v2"])
    }
}
