import SwiftUI

struct CommentSheet: View {
    let videoId: String
    @State private var comments: [Comment] = []
    @State private var newComment = ""
    @State private var isLoading = false
    @State private var isLoadingMore = false
    @State private var total = 0
    @State private var currentPage = 0
    @State private var totalPages = 0
    @Environment(\.dismiss) private var dismiss
    private let api = APIClient.shared
    private let pageSize = 20
    
    var body: some View {
        NavigationStack {
            VStack {
                if comments.isEmpty && !isLoading {
                    Text("暂无评论").foregroundColor(.secondary).padding()
                }
                List {
                    ForEach(comments) { comment in
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
                    if currentPage + 1 < totalPages {
                        Button(action: { Task { await loadMore() } }) {
                            HStack {
                                Spacer()
                                if isLoadingMore {
                                    ProgressView()
                                } else {
                                    Text("加载更多")
                                        .foregroundColor(.accentColor)
                                }
                                Spacer()
                            }
                        }
                        .disabled(isLoadingMore)
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
            .navigationTitle("评论 (\(total))")
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
        if let page: CommentPage = try? await api.get("/comment/getByVideoId?videoId=\(videoId)&page=0&pageSize=\(pageSize)&sortBy=createTime") {
            comments = page.list
            total = page.total
            currentPage = page.currentPage
            totalPages = page.totalPages
        }
        isLoading = false
    }
    
    private func loadMore() async {
        guard currentPage + 1 < totalPages else { return }
        isLoadingMore = true
        let nextPage = currentPage + 1
        if let page: CommentPage = try? await api.get("/comment/getByVideoId?videoId=\(videoId)&page=\(nextPage)&pageSize=\(pageSize)&sortBy=createTime") {
            comments += page.list
            total = page.total
            currentPage = page.currentPage
            totalPages = page.totalPages
        }
        isLoadingMore = false
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
