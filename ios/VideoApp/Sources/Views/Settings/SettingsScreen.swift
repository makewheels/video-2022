import SwiftUI

struct SettingsScreen: View {
    @EnvironmentObject var authManager: AuthManager
    @State private var showLogoutAlert = false
    
    var body: some View {
        List {
            Section {
                HStack {
                    Image(systemName: "person.circle.fill")
                        .font(.title)
                        .foregroundColor(.blue)
                    VStack(alignment: .leading) {
                        Text("当前用户")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(maskedPhone)
                            .font(.headline)
                    }
                }
            }
            
            Section {
                NavigationLink {
                    YouTubeScreen()
                } label: {
                    Label("YouTube 下载", systemImage: "play.rectangle")
                }
            }
            
            Section {
                Button(role: .destructive) {
                    showLogoutAlert = true
                } label: {
                    Label("退出登录", systemImage: "rectangle.portrait.and.arrow.right")
                }
            }
            
            Section {
                HStack {
                    Spacer()
                    Text("版本 1.0.0")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Spacer()
                }
            }
        }
        .navigationTitle("设置")
        .alert("确认退出登录？", isPresented: $showLogoutAlert) {
            Button("取消", role: .cancel) {}
            Button("退出", role: .destructive) { authManager.logout() }
        }
    }
    
    private var maskedPhone: String {
        guard let phone = authManager.userPhone, phone.count == 11 else { return "未知" }
        return "\(phone.prefix(3))****\(phone.suffix(4))"
    }
}
