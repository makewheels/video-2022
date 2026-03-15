import XCTest
@testable import VideoApp

final class APIClientTests: XCTestCase {

    // MARK: - Singleton

    func testSharedInstanceExists() {
        let client = APIClient.shared
        XCTAssertNotNil(client)
    }

    func testSharedInstanceIsSame() {
        let a = APIClient.shared
        let b = APIClient.shared
        XCTAssertTrue(a === b)
    }

    // MARK: - ApiResponse Decoding with Nested Types

    func testApiResponseWithNestedObjectDecoding() throws {
        struct VideoInfo: Decodable, Equatable {
            let id: String
            let title: String
        }

        let json = """
        {
            "code": 0,
            "message": "success",
            "data": { "id": "v123", "title": "测试视频" }
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(ApiResponse<VideoInfo>.self, from: json)
        XCTAssertTrue(response.isSuccess)
        XCTAssertEqual(response.data?.id, "v123")
        XCTAssertEqual(response.data?.title, "测试视频")
    }

    func testApiResponseWithArrayDecoding() throws {
        let json = """
        {
            "code": 0,
            "message": "ok",
            "data": ["apple", "banana", "cherry"]
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(ApiResponse<[String]>.self, from: json)
        XCTAssertTrue(response.isSuccess)
        XCTAssertEqual(response.data?.count, 3)
        XCTAssertEqual(response.data?.first, "apple")
    }

    // MARK: - ApiResponse isSuccess

    func testIsSuccessOnlyForCodeZero() throws {
        let successJSON = """
        { "code": 0, "message": null, "data": "ok" }
        """.data(using: .utf8)!
        let success = try JSONDecoder().decode(ApiResponse<String>.self, from: successJSON)
        XCTAssertTrue(success.isSuccess)

        let errorJSON = """
        { "code": 1, "message": "bad", "data": null }
        """.data(using: .utf8)!
        let error = try JSONDecoder().decode(ApiResponse<String?>.self, from: errorJSON)
        XCTAssertFalse(error.isSuccess)

        let serverErrJSON = """
        { "code": 500, "message": "internal", "data": null }
        """.data(using: .utf8)!
        let serverErr = try JSONDecoder().decode(ApiResponse<String?>.self, from: serverErrJSON)
        XCTAssertFalse(serverErr.isSuccess)
    }

    // MARK: - APIError Descriptions

    func testAPIErrorRequestFailedMessage() {
        let err = APIError.requestFailed("Connection refused")
        XCTAssertEqual(err.errorDescription, "Connection refused")
    }

    func testAPIErrorDecodingFailed() {
        let err = APIError.decodingFailed
        XCTAssertEqual(err.errorDescription, "数据解析失败")
    }

    func testAPIErrorNoData() {
        let err = APIError.noData
        XCTAssertEqual(err.errorDescription, "数据为空")
    }

    // MARK: - Malformed JSON

    func testDecodingThrowsOnInvalidJSON() {
        let badJSON = "not valid json".data(using: .utf8)!
        XCTAssertThrowsError(
            try JSONDecoder().decode(ApiResponse<String>.self, from: badJSON)
        )
    }

    func testDecodingThrowsOnMissingCodeField() {
        let json = """
        { "message": "ok", "data": "value" }
        """.data(using: .utf8)!
        XCTAssertThrowsError(
            try JSONDecoder().decode(ApiResponse<String>.self, from: json)
        )
    }
}
