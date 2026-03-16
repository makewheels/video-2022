import SwiftUI

struct SearchView: View {
    @State private var query = ""
    @State private var selectedCategory: String? = nil
    @State private var videos: [VideoItem] = []
    @State private var isLoading = false
    @State private var hasMore = false
    @State private var hasSearched = false
    @State private var total = 0
    @State private var currentPage = 0
    private let pageSize = 20
    private let api = APIClient.shared

    private let categories = [
        "音乐", "游戏", "教育", "科技", "生活",
        "娱乐", "新闻", "体育", "动漫", "美食",
        "旅行", "知识", "影视", "搞笑", "其他"
    ]

    var body: some View {
        VStack(spacing: 0) {
            // Search bar
            HStack {
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.secondary)
                    TextField("搜索视频...", text: $query)
                        .textFieldStyle(.plain)
                        .onSubmit { Task { await search() } }
                    if !query.isEmpty {
                        Button {
                            query = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .padding(8)
                .background(Color(.systemGray6))
                .cornerRadius(10)

                Button("搜索") {
                    Task { await search() }
                }
            }
            .padding(.horizontal)
            .padding(.vertical, 8)

            // Category filter
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    categoryChip("全部", isSelected: selectedCategory == nil) {
                        selectedCategory = nil
                        Task { await search() }
                    }
                    ForEach(categories, id: \.self) { cat in
                        categoryChip(cat, isSelected: selectedCategory == cat) {
                            selectedCategory = cat
                            Task { await search() }
                        }
                    }
                }
                .padding(.horizontal)
            }
            .padding(.bottom, 8)

            // Results
            if videos.isEmpty && !isLoading {
                Spacer()
                Text(hasSearched ? "未找到相关视频" : "请输入搜索关键词")
                    .foregroundColor(.secondary)
                Spacer()
            } else {
                if total > 0 {
                    Text("共 \(total) 个结果")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal)
                        .padding(.bottom, 4)
                }
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
            }
        }
        .navigationTitle("搜索")
        .navigationDestination(for: String.self) { watchId in
            WatchScreen(watchId: watchId)
        }
    }

    private func categoryChip(_ title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color.accentColor : Color(.systemGray5))
                .foregroundColor(isSelected ? .white : .primary)
                .cornerRadius(16)
        }
    }

    private func search() async {
        isLoading = true
        currentPage = 0
        do {
            var path = "/search?page=0&pageSize=\(pageSize)"
            if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                path += "&q=\(query.trimmingCharacters(in: .whitespaces).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
            }
            if let cat = selectedCategory {
                path += "&category=\(cat.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
            }
            let resp: SearchResultResponse = try await api.get(path)
            videos = resp.content
            total = resp.total
            hasMore = resp.currentPage < resp.totalPages - 1
            hasSearched = true
        } catch {
            videos = []
            hasSearched = true
        }
        isLoading = false
    }

    private func loadMore() async {
        guard !isLoading, hasMore else { return }
        isLoading = true
        let nextPage = currentPage + 1
        do {
            var path = "/search?page=\(nextPage)&pageSize=\(pageSize)"
            if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                path += "&q=\(query.trimmingCharacters(in: .whitespaces).addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
            }
            if let cat = selectedCategory {
                path += "&category=\(cat.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? "")"
            }
            let resp: SearchResultResponse = try await api.get(path)
            videos.append(contentsOf: resp.content)
            currentPage = nextPage
            hasMore = resp.currentPage < resp.totalPages - 1
            total = resp.total
        } catch {}
        isLoading = false
    }
}
