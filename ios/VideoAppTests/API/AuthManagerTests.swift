import XCTest
@testable import VideoApp

final class AuthManagerTests: XCTestCase {

    private let tokenKey = "token"
    private let clientIdKey = "clientId"
    private let sessionIdKey = "sessionId"
    private let userPhoneKey = "userPhone"

    @MainActor
    private func cleanState() {
        AuthManager.shared.logout()
    }

    // MARK: - Initial / Clean State

    @MainActor
    func testCleanState() {
        cleanState()
        let manager = AuthManager.shared
        XCTAssertFalse(manager.isLoggedIn)
        XCTAssertNil(manager.token)
        XCTAssertNil(manager.clientId)
        XCTAssertNil(manager.sessionId)
        XCTAssertNil(manager.userPhone)
    }

    // MARK: - setToken

    @MainActor
    func testSetTokenUpdatesState() {
        cleanState()
        let manager = AuthManager.shared
        manager.setToken("test-token-123")

        XCTAssertTrue(manager.isLoggedIn)
        XCTAssertEqual(manager.token, "test-token-123")
    }

    @MainActor
    func testSetTokenPersistsToUserDefaults() {
        cleanState()
        let manager = AuthManager.shared
        manager.setToken("persisted-token")

        let stored = UserDefaults.standard.string(forKey: tokenKey)
        XCTAssertEqual(stored, "persisted-token")
    }

    // MARK: - setClientId

    @MainActor
    func testSetClientId() {
        cleanState()
        let manager = AuthManager.shared
        manager.setClientId("client-abc")

        XCTAssertEqual(manager.clientId, "client-abc")
        XCTAssertEqual(UserDefaults.standard.string(forKey: clientIdKey), "client-abc")
    }

    // MARK: - setSessionId

    @MainActor
    func testSetSessionId() {
        cleanState()
        let manager = AuthManager.shared
        manager.setSessionId("sess-xyz")

        XCTAssertEqual(manager.sessionId, "sess-xyz")
        XCTAssertEqual(UserDefaults.standard.string(forKey: sessionIdKey), "sess-xyz")
    }

    // MARK: - setUserPhone

    @MainActor
    func testSetUserPhone() {
        cleanState()
        let manager = AuthManager.shared
        manager.setUserPhone("13800138000")

        XCTAssertEqual(manager.userPhone, "13800138000")
        XCTAssertEqual(UserDefaults.standard.string(forKey: userPhoneKey), "13800138000")
    }

    // MARK: - Logout

    @MainActor
    func testLogoutClearsAllFields() {
        cleanState()
        let manager = AuthManager.shared

        // Set all fields first
        manager.setToken("tok")
        manager.setClientId("cid")
        manager.setSessionId("sid")
        manager.setUserPhone("phone")

        // Verify they are set
        XCTAssertTrue(manager.isLoggedIn)
        XCTAssertNotNil(manager.token)

        // Logout
        manager.logout()

        XCTAssertFalse(manager.isLoggedIn)
        XCTAssertNil(manager.token)
        XCTAssertNil(manager.clientId)
        XCTAssertNil(manager.sessionId)
        XCTAssertNil(manager.userPhone)
    }

    @MainActor
    func testLogoutClearsUserDefaults() {
        cleanState()
        let manager = AuthManager.shared
        let defaults = UserDefaults.standard

        manager.setToken("tok")
        manager.setClientId("cid")
        manager.setSessionId("sid")
        manager.setUserPhone("phone")

        manager.logout()

        XCTAssertNil(defaults.string(forKey: tokenKey))
        XCTAssertNil(defaults.string(forKey: clientIdKey))
        XCTAssertNil(defaults.string(forKey: sessionIdKey))
        XCTAssertNil(defaults.string(forKey: userPhoneKey))
    }

    // MARK: - APIError

    func testAPIErrorDescriptions() {
        let requestErr = APIError.requestFailed("Network timeout")
        XCTAssertEqual(requestErr.errorDescription, "Network timeout")

        let decodingErr = APIError.decodingFailed
        XCTAssertEqual(decodingErr.errorDescription, "数据解析失败")

        let noDataErr = APIError.noData
        XCTAssertEqual(noDataErr.errorDescription, "数据为空")
    }

    // MARK: - ApiResponse

    func testApiResponseSuccessDecoding() throws {
        let json = """
        { "code": 0, "message": "success", "data": "hello" }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(ApiResponse<String>.self, from: json)
        XCTAssertEqual(response.code, 0)
        XCTAssertTrue(response.isSuccess)
        XCTAssertEqual(response.data, "hello")
        XCTAssertEqual(response.message, "success")
    }

    func testApiResponseFailureDecoding() throws {
        let json = """
        { "code": 500, "message": "Internal error", "data": null }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(ApiResponse<String?>.self, from: json)
        XCTAssertEqual(response.code, 500)
        XCTAssertFalse(response.isSuccess)
        XCTAssertEqual(response.message, "Internal error")
    }

    func testApiResponseNoDataField() throws {
        let json = """
        { "code": 0, "message": null }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(ApiResponse<String?>.self, from: json)
        XCTAssertTrue(response.isSuccess)
        XCTAssertNil(response.data)
        XCTAssertNil(response.message)
    }
}
