import SwiftUI

struct CommentSheet: View {
    let videoId: String
    @State private var comments: [Comment] = []
    @State private var newComment = ""
    @State private var isLoading = false
    @Environment(\.dismiss) private var dismiss
    private let api = APIClient.shared
    
    var body: some View {
        NavigationStack {
            VStack {
                if comments.isEmpty && !isLoading {
                    Text("暂无评论").foregroundColor(.secondary).padding()
                }
                List(comments) { comment in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(comment.userPhone ?? "匿名")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        Text(comment.content ?? "")
                            .font(.body)
                        HStack {
                            if let time = comment.createTime {
                                Text(time).font(.caption2).foregroundColor(.secondary)
                            }
                            if let likes = comment.likeCount, likes > 0 {
                                Label("\(likes)", systemImage: "hand.thumbsup")
                                    .font(.caption2).foregroundColor(.secondary)
                            }
                        }
                    }
                }
                .listStyle(.plain)
                
                HStack {
                    TextField("写评论...", text: $newComment)
                        .textFieldStyle(.roundedBorder)
                    Button("发送") {
                        Task { await sendComment() }
                    }
                    .disabled(newComment.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding()
            }
            .navigationTitle("评论")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
            .task { await loadComments() }
        }
    }
    
    private func loadComments() async {
        isLoading = true
        if let result: [Comment] = try? await api.get("/comment/getByVideoId?videoId=\(videoId)&skip=0&limit=20") {
            comments = result
        }
        isLoading = false
    }
    
    private func sendComment() async {
        let content = newComment.trimmingCharacters(in: .whitespaces)
        guard !content.isEmpty else { return }
        let request = AddCommentRequest(videoId: videoId, content: content, parentId: nil)
        if let _: Comment = try? await api.post("/comment/add", body: request) {
            newComment = ""
            await loadComments()
        }
    }
}
