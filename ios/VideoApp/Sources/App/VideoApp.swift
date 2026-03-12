import SwiftUI

@main
struct VideoApp: App {
    @StateObject private var authManager = AuthManager.shared
    
    var body: some Scene {
        WindowGroup {
            if authManager.isLoggedIn {
                MainTabView()
                    .environmentObject(authManager)
            } else {
                LoginScreen()
                    .environmentObject(authManager)
            }
        }
    }
}
