import SwiftUI

struct NotificationView: View {
    @State private var notifications: [NotificationItem] = []
    @State private var isLoading = false
    @State private var hasMore = true
    @State private var unreadCount = 0
    private let pageSize = 20
    private let api = APIClient.shared

    var body: some View {
        Group {
            if notifications.isEmpty && !isLoading {
                VStack(spacing: 16) {
                    Text("暂无通知")
                        .foregroundColor(.secondary)
                    Button("重新加载") { Task { await loadNotifications(refresh: true) } }
                }
            } else {
                List {
                    ForEach(notifications) { notification in
                        NotificationRow(notification: notification) {
                            Task { await markAsRead(notification.id) }
                        }
                    }
                    if hasMore {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                            .onAppear { Task { await loadMore() } }
                    }
                }
                .listStyle(.plain)
                .refreshable { await loadNotifications(refresh: true) }
            }
        }
        .navigationTitle("通知")
        .toolbar {
            if unreadCount > 0 {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("全部已读") {
                        Task { await markAllAsRead() }
                    }
                }
            }
        }
        .task { await loadNotifications(refresh: true) }
    }

    private func loadNotifications(refresh: Bool) async {
        isLoading = true
        do {
            let resp: NotificationListResponse = try await api.get(
                "/notification/getMyNotifications?page=0&pageSize=\(pageSize)")
            notifications = resp.list
            hasMore = resp.list.count >= pageSize
        } catch {}
        isLoading = false
        await loadUnreadCount()
    }

    private func loadMore() async {
        let page = notifications.count / pageSize
        do {
            let resp: NotificationListResponse = try await api.get(
                "/notification/getMyNotifications?page=\(page)&pageSize=\(pageSize)")
            notifications.append(contentsOf: resp.list)
            hasMore = resp.list.count >= pageSize
        } catch {}
    }

    private func markAsRead(_ id: String) async {
        do {
            try await api.postVoid("/notification/markAsRead",
                                   body: MarkAsReadRequest(notificationId: id))
            if let index = notifications.firstIndex(where: { $0.id == id }) {
                let old = notifications[index]
                notifications[index] = NotificationItem(
                    id: old.id, type: old.type, content: old.content,
                    fromUserId: old.fromUserId, toUserId: old.toUserId,
                    relatedVideoId: old.relatedVideoId,
                    relatedCommentId: old.relatedCommentId,
                    read: true, createTime: old.createTime)
            }
            unreadCount = max(0, unreadCount - 1)
        } catch {}
    }

    private func markAllAsRead() async {
        do {
            try await api.postVoid("/notification/markAllAsRead",
                                   body: ["placeholder": ""])
            notifications = notifications.map { n in
                NotificationItem(
                    id: n.id, type: n.type, content: n.content,
                    fromUserId: n.fromUserId, toUserId: n.toUserId,
                    relatedVideoId: n.relatedVideoId,
                    relatedCommentId: n.relatedCommentId,
                    read: true, createTime: n.createTime)
            }
            unreadCount = 0
        } catch {}
    }

    private func loadUnreadCount() async {
        do {
            let count: Int = try await api.get("/notification/getUnreadCount")
            unreadCount = count
        } catch {}
    }
}

private struct NotificationRow: View {
    let notification: NotificationItem
    let onMarkAsRead: () -> Void

    private var typeLabel: String {
        switch notification.type {
        case "COMMENT_REPLY": return "💬 评论回复"
        case "NEW_SUBSCRIBER": return "👤 新订阅"
        case "VIDEO_LIKE": return "👍 视频点赞"
        case "COMMENT_LIKE": return "👍 评论点赞"
        default: return notification.type ?? ""
        }
    }

    var body: some View {
        Button(action: {
            if !notification.read { onMarkAsRead() }
        }) {
            HStack(alignment: .top, spacing: 12) {
                if !notification.read {
                    Circle()
                        .fill(Color.blue)
                        .frame(width: 8, height: 8)
                        .padding(.top, 6)
                } else {
                    Spacer().frame(width: 8)
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text(typeLabel)
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text(notification.content ?? "")
                        .font(.body)
                        .lineLimit(2)
                        .foregroundColor(.primary)
                    if let time = notification.createTime {
                        Text(time)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
            }
            .padding(.vertical, 4)
            .opacity(notification.read ? 0.7 : 1.0)
        }
        .buttonStyle(.plain)
    }
}
