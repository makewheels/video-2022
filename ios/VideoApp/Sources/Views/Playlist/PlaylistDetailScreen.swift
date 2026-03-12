import SwiftUI

struct PlaylistDetailScreen: View {
    let playlistId: String
    @State private var items: [PlaylistItem] = []
    @State private var isLoading = true
    private let api = APIClient.shared
    
    var body: some View {
        List {
            ForEach(items) { item in
                NavigationLink(value: item.watchId ?? "") {
                    HStack(spacing: 12) {
                        AsyncImage(url: URL(string: item.coverUrl ?? "")) { img in
                            img.resizable().aspectRatio(16/9, contentMode: .fill)
                        } placeholder: {
                            Rectangle().fill(Color.gray.opacity(0.3))
                        }
                        .frame(width: 100, height: 56).cornerRadius(6).clipped()
                        
                        VStack(alignment: .leading) {
                            Text(item.title ?? "").font(.subheadline).lineLimit(2)
                            if let count = item.watchCount {
                                Text("\(count) 次观看").font(.caption).foregroundColor(.secondary)
                            }
                        }
                    }
                }
            }
            .onDelete { indexSet in
                for idx in indexSet {
                    let item = items[idx]
                    if let videoId = item.videoId {
                        Task { await removeItem(videoId) }
                    }
                }
                items.remove(atOffsets: indexSet)
            }
        }
        .listStyle(.plain)
        .navigationTitle("播放列表详情")
        .task { await loadItems() }
    }
    
    private func loadItems() async {
        if let list: [PlaylistItem] = try? await api.get("/playlist/getPlayItemListDetail?playlistId=\(playlistId)") {
            items = list
        }
        isLoading = false
    }
    
    private func removeItem(_ videoId: String) async {
        let req = DeletePlaylistItemRequest(playlistId: playlistId, deleteMode: "BY_VIDEO_ID", videoIdList: [videoId])
        try? await api.postVoid("/playlist/deletePlaylistItem", body: req)
    }
}
