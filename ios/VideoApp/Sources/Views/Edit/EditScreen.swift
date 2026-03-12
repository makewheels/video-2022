import SwiftUI

struct EditScreen: View {
    let videoId: String
    @State private var title = ""
    @State private var description = ""
    @State private var visibility = "PUBLIC"
    @State private var isLoading = true
    @State private var isSaving = false
    @State private var saved = false
    @State private var errorMessage: String?
    @Environment(\.dismiss) private var dismiss
    private let api = APIClient.shared
    private let visibilityOptions = ["PUBLIC", "UNLISTED", "PRIVATE"]
    
    var body: some View {
        Form {
            Section("视频信息") {
                TextField("标题", text: $title)
                TextField("描述", text: $description, axis: .vertical).lineLimit(3...6)
                Picker("可见性", selection: $visibility) {
                    ForEach(visibilityOptions, id: \.self) { Text($0) }
                }
            }
            
            Section {
                Button(action: { Task { await save() } }) {
                    if isSaving { ProgressView() } else { Text(saved ? "已保存 ✓" : "保存") }
                }
                .disabled(isSaving)
            }
            
            Section {
                Button("删除视频", role: .destructive) {
                    Task {
                        try? await api.getVoid("/video/delete?videoId=\(videoId)")
                        dismiss()
                    }
                }
            }
            
            if let error = errorMessage {
                Text(error).foregroundColor(.red)
            }
        }
        .navigationTitle("编辑视频")
        .task { await loadVideo() }
    }
    
    private func loadVideo() async {
        if let video: VideoItem = try? await api.get("/video/getVideoDetail?videoId=\(videoId)") {
            title = video.title ?? ""
            description = video.description ?? ""
            visibility = video.visibility ?? "PUBLIC"
        }
        isLoading = false
    }
    
    private func save() async {
        isSaving = true
        let req = UpdateVideoInfoRequest(id: videoId, title: title, description: description, visibility: visibility)
        do {
            let _: VideoItem = try await api.post("/video/updateInfo", body: req)
            saved = true
        } catch {
            errorMessage = error.localizedDescription
        }
        isSaving = false
    }
}
