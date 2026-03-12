import SwiftUI

struct LoginScreen: View {
    @EnvironmentObject var authManager: AuthManager
    @State private var phone = ""
    @State private var code = ""
    @State private var isCodeSent = false
    @State private var isLoading = false
    @State private var errorMessage: String?
    
    private let api = APIClient.shared
    
    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            
            Text("视频平台")
                .font(.largeTitle)
                .fontWeight(.bold)
            
            Spacer().frame(height: 24)
            
            TextField("手机号", text: $phone)
                .keyboardType(.phonePad)
                .textFieldStyle(.roundedBorder)
                .padding(.horizontal, 32)
            
            if isCodeSent {
                TextField("验证码", text: $code)
                    .keyboardType(.numberPad)
                    .textFieldStyle(.roundedBorder)
                    .padding(.horizontal, 32)
            }
            
            if let error = errorMessage {
                Text(error)
                    .foregroundColor(.red)
                    .font(.caption)
            }
            
            Button(action: {
                Task {
                    if isCodeSent {
                        await submitCode()
                    } else {
                        await requestCode()
                    }
                }
            }) {
                if isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                } else {
                    Text(isCodeSent ? "登录" : "获取验证码")
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isLoading)
            .padding(.horizontal, 32)
            
            Spacer()
        }
    }
    
    private func requestCode() async {
        guard phone.count == 11 else {
            errorMessage = "请输入11位手机号"
            return
        }
        isLoading = true
        errorMessage = nil
        do {
            let _: String? = try await api.getOptional("/user/requestVerificationCode?phone=\(phone)")
            isCodeSent = true
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func submitCode() async {
        guard !code.isEmpty else {
            errorMessage = "请输入验证码"
            return
        }
        isLoading = true
        errorMessage = nil
        do {
            let response: LoginResponse = try await api.get("/user/submitVerificationCode?phone=\(phone)&code=\(code)")
            if let token = response.token {
                await MainActor.run {
                    authManager.setToken(token)
                    authManager.setUserPhone(phone)
                    if let sid = response.sessionId { authManager.setSessionId(sid) }
                    if let cid = response.clientId { authManager.setClientId(cid) }
                }
            }
            // Init client and session
            let _: LoginResponse = try await api.get("/user/initClient")
            let sessionResp: LoginResponse = try await api.get("/user/initSession")
            if let cid = sessionResp.clientId { await MainActor.run { authManager.setClientId(cid) } }
            if let sid = sessionResp.sessionId { await MainActor.run { authManager.setSessionId(sid) } }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
