import SwiftUI

struct EditScreen: View {
    let videoId: String
    @State private var title = ""
    @State private var description = ""
    @State private var visibility = "PUBLIC"
    @State private var category = ""
    @State private var tags: [String] = []
    @State private var tagInput = ""
    @State private var isLoading = true
    @State private var isSaving = false
    @State private var saved = false
    @State private var errorMessage: String?
    @Environment(\.dismiss) private var dismiss
    private let api = APIClient.shared
    private let visibilityOptions = ["PUBLIC", "UNLISTED", "PRIVATE"]
    private let categoryOptions = [
        "音乐", "游戏", "教育", "科技", "生活",
        "娱乐", "新闻", "体育", "动漫", "美食",
        "旅行", "知识", "影视", "搞笑", "其他"
    ]
    
    var body: some View {
        Form {
            Section("视频信息") {
                TextField("标题", text: $title)
                TextField("描述", text: $description, axis: .vertical).lineLimit(3...6)
                Picker("可见性", selection: $visibility) {
                    ForEach(visibilityOptions, id: \.self) { Text($0) }
                }
                Picker("分类", selection: $category) {
                    Text("选择分类").tag("")
                    ForEach(categoryOptions, id: \.self) { Text($0).tag($0) }
                }
            }
            
            Section("标签") {
                if !tags.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(tags, id: \.self) { tag in
                                HStack(spacing: 4) {
                                    Text(tag).font(.caption)
                                    Button(action: { tags.removeAll { $0 == tag } }) {
                                        Image(systemName: "xmark.circle.fill")
                                            .font(.caption)
                                    }
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color.secondary.opacity(0.2))
                                .cornerRadius(12)
                            }
                        }
                    }
                }
                TextField("输入标签后按回车添加", text: $tagInput)
                    .onSubmit {
                        let trimmed = tagInput.trimmingCharacters(in: .whitespaces)
                        if !trimmed.isEmpty && !tags.contains(trimmed) {
                            tags.append(trimmed)
                        }
                        tagInput = ""
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
            tags = video.tags ?? []
            category = video.category ?? ""
        }
        isLoading = false
    }
    
    private func save() async {
        isSaving = true
        let req = UpdateVideoInfoRequest(id: videoId, title: title, description: description, visibility: visibility, tags: tags, category: category)
        do {
            let _: VideoItem = try await api.post("/video/updateInfo", body: req)
            saved = true
        } catch {
            errorMessage = error.localizedDescription
        }
        isSaving = false
    }
}
