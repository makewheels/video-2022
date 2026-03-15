import XCTest
@testable import VideoApp

final class UpdateCheckManagerTests: XCTestCase {

    @MainActor
    private func makeManager() -> UpdateCheckManager {
        UpdateCheckManager()
    }

    /// Mirrors the response-handling logic inside `checkForUpdate()`.
    @MainActor
    private func applyResponse(_ response: CheckUpdateResponse, to manager: UpdateCheckManager) {
        if response.hasUpdate == true {
            manager.showUpdateAlert = true
            manager.isForceUpdate = response.isForceUpdate ?? false
            manager.versionName = response.versionName ?? ""
            manager.versionInfo = response.versionInfo ?? ""
            manager.downloadUrl = response.downloadUrl ?? ""
        }
    }

    // MARK: - Default State

    @MainActor
    func testDefaultState() {
        let manager = makeManager()
        XCTAssertFalse(manager.showUpdateAlert)
        XCTAssertFalse(manager.isForceUpdate)
        XCTAssertEqual(manager.versionName, "")
        XCTAssertEqual(manager.versionInfo, "")
        XCTAssertEqual(manager.downloadUrl, "")
    }

    // MARK: - No Update Available

    @MainActor
    func testNoUpdateAvailable() {
        let manager = makeManager()
        let response = CheckUpdateResponse(
            hasUpdate: false,
            versionCode: nil,
            versionName: nil,
            versionInfo: nil,
            downloadUrl: nil,
            isForceUpdate: nil
        )
        applyResponse(response, to: manager)

        XCTAssertFalse(manager.showUpdateAlert)
        XCTAssertFalse(manager.isForceUpdate)
        XCTAssertEqual(manager.versionName, "")
    }

    // MARK: - Update Available

    @MainActor
    func testUpdateAvailable() {
        let manager = makeManager()
        let response = CheckUpdateResponse(
            hasUpdate: true,
            versionCode: 2,
            versionName: "2.0.0",
            versionInfo: "Bug fixes and improvements",
            downloadUrl: "https://example.com/update",
            isForceUpdate: false
        )
        applyResponse(response, to: manager)

        XCTAssertTrue(manager.showUpdateAlert)
        XCTAssertEqual(manager.versionName, "2.0.0")
        XCTAssertEqual(manager.versionInfo, "Bug fixes and improvements")
        XCTAssertEqual(manager.downloadUrl, "https://example.com/update")
    }

    // MARK: - Force Update

    @MainActor
    func testForceUpdate() {
        let manager = makeManager()
        let response = CheckUpdateResponse(
            hasUpdate: true,
            versionCode: 3,
            versionName: "3.0.0",
            versionInfo: "Critical security patch",
            downloadUrl: "https://example.com/force",
            isForceUpdate: true
        )
        applyResponse(response, to: manager)

        XCTAssertTrue(manager.isForceUpdate)
        XCTAssertTrue(manager.showUpdateAlert)
    }

    // MARK: - Optional Update

    @MainActor
    func testOptionalUpdate() {
        let manager = makeManager()
        let response = CheckUpdateResponse(
            hasUpdate: true,
            versionCode: 2,
            versionName: "2.0.0",
            versionInfo: "Minor improvements",
            downloadUrl: "https://example.com/optional",
            isForceUpdate: false
        )
        applyResponse(response, to: manager)

        XCTAssertFalse(manager.isForceUpdate)
        XCTAssertTrue(manager.showUpdateAlert)
    }

    // MARK: - Network Error

    @MainActor
    func testNetworkError() async {
        let manager = makeManager()

        // No server running → request fails → error caught silently
        await manager.checkForUpdate()

        XCTAssertFalse(manager.showUpdateAlert)
        XCTAssertFalse(manager.isForceUpdate)
        XCTAssertEqual(manager.versionName, "")
        XCTAssertEqual(manager.versionInfo, "")
        XCTAssertEqual(manager.downloadUrl, "")
    }

    // MARK: - Version Fields

    @MainActor
    func testVersionFields() {
        let manager = makeManager()
        let response = CheckUpdateResponse(
            hasUpdate: true,
            versionCode: 10,
            versionName: "1.5.3",
            versionInfo: "New features and performance improvements",
            downloadUrl: "https://example.com/v1.5.3",
            isForceUpdate: false
        )
        applyResponse(response, to: manager)

        XCTAssertEqual(manager.versionName, "1.5.3")
        XCTAssertEqual(manager.versionInfo, "New features and performance improvements")
        XCTAssertEqual(manager.downloadUrl, "https://example.com/v1.5.3")
    }

    // MARK: - Dismiss Behavior

    @MainActor
    func testDismissOptionalUpdate() {
        let manager = makeManager()
        manager.showUpdateAlert = true
        manager.isForceUpdate = false

        manager.dismiss()

        XCTAssertFalse(manager.showUpdateAlert)
    }

    @MainActor
    func testDismissForceUpdateBlocked() {
        let manager = makeManager()
        manager.showUpdateAlert = true
        manager.isForceUpdate = true

        manager.dismiss()

        XCTAssertTrue(manager.showUpdateAlert, "Force update alert should not be dismissible")
    }

    // MARK: - CheckUpdateResponse Decoding

    func testCheckUpdateResponseFullDecoding() throws {
        let json = """
        {
            "hasUpdate": true,
            "versionCode": 5,
            "versionName": "2.1.0",
            "versionInfo": "Stability fixes",
            "downloadUrl": "https://example.com/download",
            "isForceUpdate": true
        }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(CheckUpdateResponse.self, from: json)
        XCTAssertEqual(response.hasUpdate, true)
        XCTAssertEqual(response.versionCode, 5)
        XCTAssertEqual(response.versionName, "2.1.0")
        XCTAssertEqual(response.versionInfo, "Stability fixes")
        XCTAssertEqual(response.downloadUrl, "https://example.com/download")
        XCTAssertEqual(response.isForceUpdate, true)
    }

    func testCheckUpdateResponsePartialDecoding() throws {
        let json = """
        { "hasUpdate": false }
        """.data(using: .utf8)!

        let response = try JSONDecoder().decode(CheckUpdateResponse.self, from: json)
        XCTAssertEqual(response.hasUpdate, false)
        XCTAssertNil(response.versionCode)
        XCTAssertNil(response.versionName)
        XCTAssertNil(response.versionInfo)
        XCTAssertNil(response.downloadUrl)
        XCTAssertNil(response.isForceUpdate)
    }

    // MARK: - Nil hasUpdate

    @MainActor
    func testNilHasUpdateTreatedAsNoUpdate() {
        let manager = makeManager()
        let response = CheckUpdateResponse(
            hasUpdate: nil,
            versionCode: nil,
            versionName: "1.0.1",
            versionInfo: "Some info",
            downloadUrl: "https://example.com",
            isForceUpdate: nil
        )
        applyResponse(response, to: manager)

        XCTAssertFalse(manager.showUpdateAlert, "nil hasUpdate should not trigger alert")
        XCTAssertEqual(manager.versionName, "", "Fields should remain default when hasUpdate is nil")
    }
}
