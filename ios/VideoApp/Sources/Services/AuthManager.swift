import Foundation

@MainActor
class AuthManager: ObservableObject {
    static let shared = AuthManager()
    
    @Published var isLoggedIn: Bool = false
    @Published var token: String?
    @Published var clientId: String?
    @Published var sessionId: String?
    @Published var userPhone: String?
    
    private let defaults = UserDefaults.standard
    
    private init() {
        self.token = defaults.string(forKey: "token")
        self.clientId = defaults.string(forKey: "clientId")
        self.sessionId = defaults.string(forKey: "sessionId")
        self.userPhone = defaults.string(forKey: "userPhone")
        self.isLoggedIn = token != nil
    }
    
    func setToken(_ token: String) {
        self.token = token
        defaults.set(token, forKey: "token")
        self.isLoggedIn = true
    }
    
    func setClientId(_ id: String) {
        self.clientId = id
        defaults.set(id, forKey: "clientId")
    }
    
    func setSessionId(_ id: String) {
        self.sessionId = id
        defaults.set(id, forKey: "sessionId")
    }
    
    func setUserPhone(_ phone: String) {
        self.userPhone = phone
        defaults.set(phone, forKey: "userPhone")
    }
    
    func logout() {
        token = nil
        clientId = nil
        sessionId = nil
        userPhone = nil
        isLoggedIn = false
        defaults.removeObject(forKey: "token")
        defaults.removeObject(forKey: "clientId")
        defaults.removeObject(forKey: "sessionId")
        defaults.removeObject(forKey: "userPhone")
    }
}
