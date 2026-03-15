import SwiftUI

@main
struct VideoApp: App {
    @StateObject private var authManager = AuthManager.shared
    @StateObject private var updateManager = UpdateCheckManager.shared
    
    var body: some Scene {
        WindowGroup {
            Group {
                if authManager.isLoggedIn {
                    MainTabView()
                        .environmentObject(authManager)
                } else {
                    LoginScreen()
                        .environmentObject(authManager)
                }
            }
            .modifier(UpdateAlertModifier(updateManager: updateManager))
            .task { await updateManager.checkForUpdate() }
        }
    }
}
