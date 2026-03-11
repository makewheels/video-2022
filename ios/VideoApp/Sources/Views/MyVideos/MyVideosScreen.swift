import SwiftUI

struct MyVideosScreen: View {
    @State private var videos: [VideoItem] = []
    @State private var keyword = ""
    @State private var isLoading = false
    @State private var showDeleteAlert = false
    @State private var deleteTarget: String?
    private let api = APIClient.shared
    
    var body: some View {
        VStack {
            HStack {
                TextField("搜索视频", text: $keyword)
                    .textFieldStyle(.roundedBorder)
                Button("搜索") { Task { await loadVideos() } }
            }
            .padding(.horizontal)
            
            if videos.isEmpty && !isLoading {
                Spacer()
                Text("暂无视频").foregroundColor(.secondary)
                Spacer()
            } else {
                List {
                    ForEach(videos) { video in
                        NavigationLink(value: video.watchId ?? "") {
                            VideoCard(video: video)
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                deleteTarget = video.id
                                showDeleteAlert = true
                            } label: {
                                Label("删除", systemImage: "trash")
                            }
                        }
                        .contextMenu {
                            NavigationLink(value: "edit:\(video.id)") {
                                Label("编辑", systemImage: "pencil")
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("我的视频")
        .navigationDestination(for: String.self) { value in
            if value.hasPrefix("edit:") {
                EditScreen(videoId: String(value.dropFirst(5)))
            } else {
                WatchScreen(watchId: value)
            }
        }
        .alert("确认删除", isPresented: $showDeleteAlert) {
            Button("取消", role: .cancel) {}
            Button("删除", role: .destructive) {
                if let id = deleteTarget { Task { await deleteVideo(id) } }
            }
        }
        .task { await loadVideos() }
    }
    
    private func loadVideos() async {
        isLoading = true
        let kw = keyword.isEmpty ? "" : "&keyword=\(keyword)"
        if let resp: VideoListResponse = try? await api.get("/video/getMyVideoList?skip=0&limit=20\(kw)") {
            videos = resp.list
        }
        isLoading = false
    }
    
    private func deleteVideo(_ id: String) async {
        try? await api.getVoid("/video/delete?videoId=\(id)")
        videos.removeAll { $0.id == id }
    }
}
