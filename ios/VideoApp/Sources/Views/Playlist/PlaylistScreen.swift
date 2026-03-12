import SwiftUI

struct PlaylistScreen: View {
    @State private var playlists: [Playlist] = []
    @State private var isLoading = false
    @State private var showCreateDialog = false
    @State private var newTitle = ""
    private let api = APIClient.shared
    
    var body: some View {
        Group {
            if playlists.isEmpty && !isLoading {
                VStack(spacing: 16) {
                    Image(systemName: "list.bullet.rectangle")
                        .font(.system(size: 48))
                        .foregroundColor(.secondary)
                    Text("暂无播放列表").foregroundColor(.secondary)
                }
            } else {
                List(playlists) { playlist in
                    NavigationLink(value: playlist.id) {
                        VStack(alignment: .leading) {
                            Text(playlist.title ?? "未命名").font(.headline)
                            if let time = playlist.createTime {
                                Text(time).font(.caption).foregroundColor(.secondary)
                            }
                        }
                    }
                }
                .listStyle(.plain)
            }
        }
        .navigationTitle("播放列表")
        .navigationDestination(for: String.self) { id in
            PlaylistDetailScreen(playlistId: id)
        }
        .toolbar {
            Button(action: { showCreateDialog = true }) {
                Image(systemName: "plus")
            }
        }
        .alert("新建播放列表", isPresented: $showCreateDialog) {
            TextField("标题", text: $newTitle)
            Button("创建") { Task { await createPlaylist() } }
            Button("取消", role: .cancel) { newTitle = "" }
        }
        .task { await loadPlaylists() }
    }
    
    private func loadPlaylists() async {
        isLoading = true
        if let list: [Playlist] = try? await api.get("/playlist/getMyPlaylistByPage?skip=0&limit=20") {
            playlists = list
        }
        isLoading = false
    }
    
    private func createPlaylist() async {
        guard !newTitle.isEmpty else { return }
        let req = CreatePlaylistRequest(title: newTitle, description: nil)
        let _: Playlist? = try? await api.post("/playlist/createPlaylist", body: req)
        newTitle = ""
        await loadPlaylists()
    }
}
