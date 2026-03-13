import Foundation
import Security

@MainActor
class AuthManager: ObservableObject {
    static let shared = AuthManager()

    @Published var isLoggedIn: Bool = false
    @Published var token: String?
    @Published var clientId: String?
    @Published var sessionId: String?
    @Published var userPhone: String?

    private static let serviceName = "com.github.makewheels.video2022"

    private init() {
        self.token = Self.keychainRead(key: "token")
        self.clientId = Self.keychainRead(key: "clientId")
        self.sessionId = Self.keychainRead(key: "sessionId")
        self.userPhone = Self.keychainRead(key: "userPhone")
        self.isLoggedIn = token != nil
    }

    // MARK: - Keychain helpers

    private static func keychainRead(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func keychainWrite(key: String, value: String) {
        keychainDelete(key: key)
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemAdd(query as CFDictionary, nil)
    }

    private static func keychainDelete(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
    }

    // MARK: - Public API

    func setToken(_ token: String) {
        self.token = token
        Self.keychainWrite(key: "token", value: token)
        self.isLoggedIn = true
    }

    func setClientId(_ id: String) {
        self.clientId = id
        Self.keychainWrite(key: "clientId", value: id)
    }

    func setSessionId(_ id: String) {
        self.sessionId = id
        Self.keychainWrite(key: "sessionId", value: id)
    }

    func setUserPhone(_ phone: String) {
        self.userPhone = phone
        Self.keychainWrite(key: "userPhone", value: phone)
    }

    func logout() {
        token = nil
        clientId = nil
        sessionId = nil
        userPhone = nil
        isLoggedIn = false
        Self.keychainDelete(key: "token")
        Self.keychainDelete(key: "clientId")
        Self.keychainDelete(key: "sessionId")
        Self.keychainDelete(key: "userPhone")
    }
}
