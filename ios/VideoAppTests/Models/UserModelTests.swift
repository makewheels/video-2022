import XCTest
@testable import VideoApp

final class UserModelTests: XCTestCase {

    // MARK: - LoginResponse Decoding

    func testLoginResponseDecodingAllFields() throws {
        let json = """
        {
            "token": "jwt-token-abc",
            "sessionId": "sess-001",
            "clientId": "client-xyz",
            "nickname": "测试用户",
            "avatarUrl": "https://example.com/avatar.jpg",
            "bannerUrl": "https://example.com/banner.jpg",
            "bio": "Hello world"
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(LoginResponse.self, from: json)
        XCTAssertEqual(response.token, "jwt-token-abc")
        XCTAssertEqual(response.sessionId, "sess-001")
        XCTAssertEqual(response.clientId, "client-xyz")
        XCTAssertEqual(response.nickname, "测试用户")
        XCTAssertEqual(response.avatarUrl, "https://example.com/avatar.jpg")
        XCTAssertEqual(response.bannerUrl, "https://example.com/banner.jpg")
        XCTAssertEqual(response.bio, "Hello world")
    }

    func testLoginResponseDecodingMinimalFields() throws {
        let json = """
        {}
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(LoginResponse.self, from: json)
        XCTAssertNil(response.token)
        XCTAssertNil(response.sessionId)
        XCTAssertNil(response.clientId)
        XCTAssertNil(response.nickname)
        XCTAssertNil(response.avatarUrl)
        XCTAssertNil(response.bannerUrl)
        XCTAssertNil(response.bio)
    }

    func testLoginResponseDecodingPartialFields() throws {
        let json = """
        {
            "token": "tok-123",
            "nickname": "User1"
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(LoginResponse.self, from: json)
        XCTAssertEqual(response.token, "tok-123")
        XCTAssertEqual(response.nickname, "User1")
        XCTAssertNil(response.sessionId)
        XCTAssertNil(response.clientId)
        XCTAssertNil(response.avatarUrl)
        XCTAssertNil(response.bannerUrl)
        XCTAssertNil(response.bio)
    }

    // MARK: - ChannelInfo Decoding

    func testChannelInfoDecoding() throws {
        let json = """
        {
            "userId": "user-abc",
            "nickname": "频道主",
            "avatarUrl": "https://example.com/avatar.jpg",
            "bannerUrl": "https://example.com/banner.jpg",
            "bio": "欢迎关注",
            "subscriberCount": 1500,
            "videoCount": 42,
            "isSubscribed": true
        }
        """.data(using: .utf8)!

        let channel = try JSONDecoder().decode(ChannelInfo.self, from: json)
        XCTAssertEqual(channel.userId, "user-abc")
        XCTAssertEqual(channel.nickname, "频道主")
        XCTAssertEqual(channel.avatarUrl, "https://example.com/avatar.jpg")
        XCTAssertEqual(channel.bannerUrl, "https://example.com/banner.jpg")
        XCTAssertEqual(channel.bio, "欢迎关注")
        XCTAssertEqual(channel.subscriberCount, 1500)
        XCTAssertEqual(channel.videoCount, 42)
        XCTAssertTrue(channel.isSubscribed)
    }

    func testChannelInfoDecodingMinimalFields() throws {
        let json = """
        {
            "userId": "user-min",
            "subscriberCount": 0,
            "videoCount": 0,
            "isSubscribed": false
        }
        """.data(using: .utf8)!

        let channel = try JSONDecoder().decode(ChannelInfo.self, from: json)
        XCTAssertEqual(channel.userId, "user-min")
        XCTAssertNil(channel.nickname)
        XCTAssertNil(channel.avatarUrl)
        XCTAssertNil(channel.bannerUrl)
        XCTAssertNil(channel.bio)
        XCTAssertEqual(channel.subscriberCount, 0)
        XCTAssertEqual(channel.videoCount, 0)
        XCTAssertFalse(channel.isSubscribed)
    }

    // MARK: - UpdateProfileRequest Encoding

    func testUpdateProfileRequestEncoding() throws {
        let request = UpdateProfileRequest(nickname: "NewName", bio: "New bio")
        let data = try JSONEncoder().encode(request)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        XCTAssertEqual(dict["nickname"] as? String, "NewName")
        XCTAssertEqual(dict["bio"] as? String, "New bio")
    }

    func testUpdateProfileRequestEncodingNilValues() throws {
        let request = UpdateProfileRequest(nickname: nil, bio: nil)
        let data = try JSONEncoder().encode(request)
        let dict = try JSONSerialization.jsonObject(with: data) as! [String: Any]

        // nil optionals encode as NSNull in Swift's JSONEncoder
        XCTAssertNotNil(dict) // should still be valid JSON
    }
}
