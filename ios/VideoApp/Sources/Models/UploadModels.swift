import Foundation

struct UploadCredentials: Decodable {
    let bucket: String
    let accessKeyId: String
    let endpoint: String
    let secretKey: String
    let provider: String
    let sessionToken: String
    let expiration: String
    let key: String
}
