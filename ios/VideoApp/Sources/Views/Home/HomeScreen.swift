import SwiftUI

struct HomeScreen: View {
    @State private var videos: [VideoItem] = []
    @State private var isLoading = false
    @State private var hasMore = true
    private let pageSize = 20
    private let api = APIClient.shared
    
    var body: some View {
        Group {
            if videos.isEmpty && !isLoading {
                VStack(spacing: 16) {
                    Text("暂无视频")
                        .foregroundColor(.secondary)
                    Button("重新加载") { Task { await loadVideos(refresh: true) } }
                }
            } else {
                List {
                    ForEach(videos) { video in
                        NavigationLink(value: video.watchId ?? "") {
                            VideoCard(video: video)
                        }
                    }
                    if hasMore {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .onAppear { Task { await loadMore() } }
                    }
                }
                .listStyle(.plain)
                .refreshable { await loadVideos(refresh: true) }
            }
        }
        .navigationTitle("首页")
        .navigationDestination(for: String.self) { watchId in
            WatchScreen(watchId: watchId)
        }
        .task { await loadVideos(refresh: true) }
    }
    
    private func loadVideos(refresh: Bool) async {
        isLoading = true
        do {
            let resp: VideoListResponse = try await api.get("/video/getPublicVideoList?skip=0&limit=\(pageSize)")
            videos = resp.list
            hasMore = resp.list.count >= pageSize
        } catch {}
        isLoading = false
    }
    
    private func loadMore() async {
        guard !isLoading, hasMore else { return }
        isLoading = true
        do {
            let resp: VideoListResponse = try await api.get("/video/getPublicVideoList?skip=\(videos.count)&limit=\(pageSize)")
            videos.append(contentsOf: resp.list)
            hasMore = resp.list.count >= pageSize
        } catch {}
        isLoading = false
    }
}
