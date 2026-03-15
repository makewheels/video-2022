import Foundation

struct CheckUpdateResponse: Decodable {
    let hasUpdate: Bool?
    let versionCode: Int?
    let versionName: String?
    let versionInfo: String?
    let downloadUrl: String?
    let isForceUpdate: Bool?
}
