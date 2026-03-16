import XCTest
@testable import VideoApp

final class SearchResultTests: XCTestCase {

    func testSearchResultResponseDecoding() throws {
        let json = """
        {
            "content": [
                { "id": "v1", "title": "测试视频" },
                { "id": "v2", "title": "另一个视频" }
            ],
            "total": 42,
            "totalPages": 3,
            "currentPage": 0,
            "pageSize": 20
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(SearchResultResponse.self, from: json)
        XCTAssertEqual(response.total, 42)
        XCTAssertEqual(response.totalPages, 3)
        XCTAssertEqual(response.currentPage, 0)
        XCTAssertEqual(response.pageSize, 20)
        XCTAssertEqual(response.content.count, 2)
        XCTAssertEqual(response.content[0].id, "v1")
        XCTAssertEqual(response.content[0].title, "测试视频")
    }

    func testSearchResultResponseEmptyContent() throws {
        let json = """
        {
            "content": [],
            "total": 0,
            "totalPages": 0,
            "currentPage": 0,
            "pageSize": 20
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(SearchResultResponse.self, from: json)
        XCTAssertEqual(response.total, 0)
        XCTAssertTrue(response.content.isEmpty)
        XCTAssertEqual(response.totalPages, 0)
    }

    func testSearchResultResponseWrappedInApiResponse() throws {
        let json = """
        {
            "code": 0,
            "message": "success",
            "data": {
                "content": [{ "id": "v1", "title": "搜索结果" }],
                "total": 1,
                "totalPages": 1,
                "currentPage": 0,
                "pageSize": 20
            }
        }
        """.data(using: .utf8)!

        let apiResp = try JSONDecoder().decode(ApiResponse<SearchResultResponse>.self, from: json)
        XCTAssertTrue(apiResp.isSuccess)
        XCTAssertNotNil(apiResp.data)
        XCTAssertEqual(apiResp.data?.content.count, 1)
        XCTAssertEqual(apiResp.data?.content[0].title, "搜索结果")
    }

    func testSearchResultResponseSinglePage() throws {
        let json = """
        {
            "content": [{ "id": "v1" }],
            "total": 1,
            "totalPages": 1,
            "currentPage": 0,
            "pageSize": 20
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(SearchResultResponse.self, from: json)
        XCTAssertEqual(response.totalPages, 1)
        XCTAssertEqual(response.currentPage, 0)
    }
}
