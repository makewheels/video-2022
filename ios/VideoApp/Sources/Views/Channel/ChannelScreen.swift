import SwiftUI

struct ChannelScreen: View {
    let userId: String
    @State private var channel: ChannelInfo?
    @State private var videos: [VideoItem] = []
    @State private var isLoading = true
    @State private var subscribing = false
    private let api = APIClient.shared
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // Banner
                ZStack {
                    if let bannerUrl = channel?.bannerUrl, let url = URL(string: bannerUrl) {
                        AsyncImage(url: url) { image in
                            image.resizable().aspectRatio(contentMode: .fill)
                        } placeholder: {
                            Rectangle().fill(Color.blue.opacity(0.3))
                        }
                    } else {
                        LinearGradient(
                            colors: [.blue.opacity(0.6), .purple.opacity(0.4)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    }
                }
                .frame(height: 150)
                .clipped()
                
                // Channel info
                HStack(alignment: .top, spacing: 16) {
                    // Avatar
                    ZStack {
                        if let avatarUrl = channel?.avatarUrl, let url = URL(string: avatarUrl) {
                            AsyncImage(url: url) { image in
                                image.resizable().aspectRatio(contentMode: .fill)
                            } placeholder: {
                                Circle().fill(Color.blue)
                            }
                        } else {
                            Circle().fill(Color.blue)
                                .overlay(
                                    Text(String((channel?.nickname ?? "?").prefix(1)).uppercased())
                                        .font(.title)
                                        .foregroundColor(.white)
                                )
                        }
                    }
                    .frame(width: 64, height: 64)
                    .clipShape(Circle())
                    .offset(y: -20)
                    
                    VStack(alignment: .leading, spacing: 4) {
                        Text(channel?.nickname ?? "未命名频道")
                            .font(.title2)
                            .fontWeight(.bold)
                        
                        Text("\(channel?.subscriberCount ?? 0) 位订阅者 · \(channel?.videoCount ?? 0) 个视频")
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        if let bio = channel?.bio, !bio.isEmpty {
                            Text(bio)
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .padding(.top, 4)
                        }
                    }
                    
                    Spacer()
                    
                    Button(action: toggleSubscribe) {
                        Text(channel?.isSubscribed == true ? "已订阅" : "订阅")
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(channel?.isSubscribed == true ? .gray : .blue)
                    .disabled(subscribing)
                }
                .padding()
                
                Divider()
                
                // Videos section
                Text("视频")
                    .font(.headline)
                    .padding(.horizontal)
                    .padding(.top, 8)
                
                if videos.isEmpty {
                    Text("暂无视频")
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 32)
                } else {
                    LazyVStack(spacing: 0) {
                        ForEach(videos) { video in
                            NavigationLink(value: video.watchId ?? "") {
                                VideoCard(video: video)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(for: String.self) { watchId in
            WatchScreen(watchId: watchId)
        }
        .task { await loadData() }
    }
    
    private func loadData() async {
        isLoading = true
        do {
            let ch: ChannelInfo = try await api.get("/user/getChannel?userId=\(userId)")
            channel = ch
        } catch {}
        
        do {
            let resp: VideoListResponse = try await api.get("/video/getPublicVideoList?skip=0&limit=50")
            videos = resp.list.filter { $0.uploaderId == userId }
        } catch {}
        isLoading = false
    }
    
    private func toggleSubscribe() {
        subscribing = true
        Task {
            do {
                if channel?.isSubscribed == true {
                    try await api.getVoid("/subscription/unsubscribe?channelUserId=\(userId)")
                } else {
                    try await api.getVoid("/subscription/subscribe?channelUserId=\(userId)")
                }
                await loadData()
            } catch {}
            subscribing = false
        }
    }
}
