import SwiftUI

struct YouTubeScreen: View {
    @State private var url = ""
    @State private var isLoading = false
    @State private var isSuccess = false
    @State private var watchUrl: String?
    @State private var errorMessage: String?
    private let api = APIClient.shared
    
    var body: some View {
        VStack(spacing: 24) {
            Text("YouTube 下载").font(.title2).fontWeight(.semibold)
            
            TextField("YouTube 链接", text: $url)
                .textFieldStyle(.roundedBorder)
                .padding(.horizontal)
            
            Button(action: { Task { await download() } }) {
                if isLoading {
                    ProgressView()
                } else {
                    Text("下载")
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(isLoading || url.isEmpty)
            
            if isSuccess {
                Label("下载任务已创建", systemImage: "checkmark.circle.fill")
                    .foregroundColor(.green)
            }
            
            if let error = errorMessage {
                Text(error).foregroundColor(.red).font(.caption)
            }
            
            Spacer()
        }
        .padding(.top, 32)
        .navigationTitle("YouTube 下载")
    }
    
    private func download() async {
        isLoading = true
        errorMessage = nil
        let req = CreateVideoRequest(videoType: "YOUTUBE", youtubeUrl: url)
        do {
            let resp: CreateVideoResponse = try await api.post("/video/create", body: req)
            isSuccess = true
            watchUrl = resp.watchUrl
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
