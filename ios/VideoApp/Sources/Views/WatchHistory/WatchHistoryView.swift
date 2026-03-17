import SwiftUI

struct WatchHistoryView: View {
    @State private var items: [WatchHistoryItem] = []
    @State private var isLoading = false
    @State private var total = 0
    @State private var page = 0
    @State private var showClearAlert = false
    private let pageSize = 20
    private let api = APIClient.shared

    var body: some View {
        Group {
            if items.isEmpty && !isLoading {
                VStack(spacing: 16) {
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 48))
                        .foregroundStyle(.secondary)
                    Text("暂无观看历史")
                        .foregroundStyle(.secondary)
                }
            } else {
                List {
                    ForEach(items) { item in
                        NavigationLink(value: item.videoId) {
                            WatchHistoryRow(item: item)
                        }
                    }
                    if items.count < total {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .onAppear { Task { await loadMore() } }
                    }
                }
                .listStyle(.plain)
                .refreshable { await loadHistory(refresh: true) }
            }
        }
        .navigationTitle("观看历史")
        .toolbar {
            if !items.isEmpty {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(role: .destructive) {
                        showClearAlert = true
                    } label: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
        .alert("清除观看历史", isPresented: $showClearAlert) {
            Button("取消", role: .cancel) {}
            Button("清除", role: .destructive) { Task { await clearHistory() } }
        } message: {
            Text("确定要清除所有观看历史吗？")
        }
        .navigationDestination(for: String.self) { videoId in
            WatchScreen(watchId: videoId)
        }
        .task { await loadHistory(refresh: true) }
    }

    private func loadHistory(refresh: Bool) async {
        isLoading = true
        let targetPage = refresh ? 0 : page
        do {
            let resp: WatchHistoryResponse = try await api.get(
                "/watchHistory/getMyHistory?page=\(targetPage)&pageSize=\(pageSize)"
            )
            if refresh {
                items = resp.list
            } else {
                items.append(contentsOf: resp.list)
            }
            total = resp.total
            page = targetPage + 1
        } catch {
            print("加载观看历史失败: \(error)")
        }
        isLoading = false
    }

    private func loadMore() async {
        await loadHistory(refresh: false)
    }

    private func clearHistory() async {
        do {
            try await api.getVoid("/watchHistory/clear")
            items = []
            total = 0
            page = 0
        } catch {
            print("清除观看历史失败: \(error)")
        }
    }
}

private struct WatchHistoryRow: View {
    let item: WatchHistoryItem

    var body: some View {
        HStack(spacing: 12) {
            if let url = item.coverUrl, let imageURL = URL(string: url) {
                AsyncImage(url: imageURL) { image in
                    image.resizable().aspectRatio(16/9, contentMode: .fill)
                } placeholder: {
                    Rectangle().fill(Color.gray.opacity(0.2))
                }
                .frame(width: 140, height: 79)
                .clipShape(RoundedRectangle(cornerRadius: 4))
            } else {
                Rectangle()
                    .fill(Color.gray.opacity(0.2))
                    .frame(width: 140, height: 79)
                    .clipShape(RoundedRectangle(cornerRadius: 4))
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(item.title ?? "未命名视频")
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .lineLimit(2)

                Text("观看时间: \(item.watchTime ?? "")")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}
