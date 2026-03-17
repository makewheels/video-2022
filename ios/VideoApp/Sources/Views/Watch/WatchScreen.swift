import SwiftUI
import AVKit

struct WatchScreen: View {
    let watchId: String
    @State private var watchInfo: WatchInfo?
    @State private var likeStatus: LikeStatus?
    @State private var commentCount = 0
    @State private var isLoading = true
    @State private var showComments = false
    @State private var showShareSheet = false
    @State private var player: AVPlayer?
    
    private let api = APIClient.shared
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                // Video Player
                if let player = player {
                    VideoPlayer(player: player)
                        .frame(height: 220)
                        .onDisappear { player.pause() }
                } else if isLoading {
                    ProgressView()
                        .frame(height: 220)
                        .frame(maxWidth: .infinity)
                } else {
                    Rectangle()
                        .fill(Color.black)
                        .frame(height: 220)
                        .overlay(Text("无法播放").foregroundColor(.white))
                }
                
                VStack(alignment: .leading, spacing: 12) {
                    Text("视频 \(watchId)")
                        .font(.title3)
                        .fontWeight(.semibold)
                    
                    // Like/Dislike buttons
                    if let status = likeStatus {
                        HStack(spacing: 24) {
                            Button(action: { Task { await like() } }) {
                                Label("\(status.likeCount)", systemImage: status.userAction == "LIKE" ? "hand.thumbsup.fill" : "hand.thumbsup")
                            }
                            Button(action: { Task { await dislike() } }) {
                                Label("\(status.dislikeCount)", systemImage: status.userAction == "DISLIKE" ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                            }
                            Spacer()
                            Button(action: { showComments = true }) {
                                Label("\(commentCount) 评论", systemImage: "bubble.right")
                            }
                            Button(action: { showShareSheet = true }) {
                                Label("分享", systemImage: "square.and.arrow.up")
                            }
                        }
                        .font(.subheadline)
                    }
                }
                .padding(.horizontal)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showComments) {
            if let info = watchInfo {
                CommentSheet(videoId: info.videoId)
            }
        }
        .sheet(isPresented: $showShareSheet) {
            if let info = watchInfo,
               let url = URL(string: "\(AppConfig.webBaseURL)/watch/\(info.videoId)") {
                ShareSheet(activityItems: [url])
            }
        }
        .task { await loadWatchInfo() }
    }
    
    private func loadWatchInfo() async {
        do {
            let info: WatchInfo = try await api.get("/watchController/getWatchInfo?watchId=\(watchId)")
            watchInfo = info
            isLoading = false
            
            if let urlStr = info.multivariantPlaylistUrl, let url = URL(string: urlStr) {
                let avPlayer = AVPlayer(url: url)
                if let progress = info.progressInMillis, progress > 0 {
                    await avPlayer.seek(to: CMTime(value: progress, timescale: 1000))
                }
                player = avPlayer
                avPlayer.play()
            }
            
            // Load like status and comment count
            if let status: LikeStatus = try? await api.get("/videoLike/getStatus?videoId=\(info.videoId)") {
                likeStatus = status
            }
            if let count: CommentCount = try? await api.get("/comment/getCount?videoId=\(info.videoId)") {
                commentCount = count.count
            }
        } catch {
            isLoading = false
        }
    }
    
    private func like() async {
        guard let info = watchInfo else { return }
        try? await api.getVoid("/videoLike/like?videoId=\(info.videoId)")
        if let status: LikeStatus = try? await api.get("/videoLike/getStatus?videoId=\(info.videoId)") {
            likeStatus = status
        }
    }
    
    private func dislike() async {
        guard let info = watchInfo else { return }
        try? await api.getVoid("/videoLike/dislike?videoId=\(info.videoId)")
        if let status: LikeStatus = try? await api.get("/videoLike/getStatus?videoId=\(info.videoId)") {
            likeStatus = status
        }
    }
}
