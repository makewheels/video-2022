import SwiftUI

struct SettingsScreen: View {
    @EnvironmentObject var authManager: AuthManager
    @State private var showLogoutAlert = false
    @State private var nickname: String = ""
    @State private var bio: String = ""
    @State private var isSaving = false
    @State private var saveMessage: String?
    private let api = APIClient.shared
    
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
            
            Section(header: Text("编辑资料")) {
                TextField("昵称", text: $nickname)
                TextField("个人简介", text: $bio)
                
                Button {
                    saveProfile()
                } label: {
                    HStack {
                        Text("保存")
                        if isSaving {
                            Spacer()
                            ProgressView()
                        }
                    }
                }
                .disabled(isSaving)
                
                if let msg = saveMessage {
                    Text(msg)
                        .font(.caption)
                        .foregroundColor(msg.contains("失败") ? .red : .green)
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
    
    private func saveProfile() {
        isSaving = true
        saveMessage = nil
        Task {
            do {
                let request = UpdateProfileRequest(
                    nickname: nickname.isEmpty ? nil : nickname,
                    bio: bio.isEmpty ? nil : bio
                )
                try await api.postVoid("/user/updateProfile", body: request)
                saveMessage = "保存成功"
            } catch {
                saveMessage = "保存失败"
            }
            isSaving = false
        }
    }
}
