import SwiftUI

struct MainTabView: View {
    var body: some View {
        TabView {
            NavigationStack {
                HomeScreen()
            }
            .tabItem {
                Label("首页", systemImage: "house.fill")
            }
            
            NavigationStack {
                SearchView()
            }
            .tabItem {
                Label("搜索", systemImage: "magnifyingglass")
            }
            
            NavigationStack {
                NotificationView()
            }
            .tabItem {
                Label("通知", systemImage: "bell.fill")
            }
            
            NavigationStack {
                PlaylistScreen()
            }
            .tabItem {
                Label("播放列表", systemImage: "list.bullet.rectangle")
            }
            
            NavigationStack {
                UploadScreen()
            }
            .tabItem {
                Label("上传", systemImage: "plus.circle.fill")
            }
            
            NavigationStack {
                MyVideosScreen()
            }
            .tabItem {
                Label("我的", systemImage: "play.rectangle.on.rectangle")
            }
            
            NavigationStack {
                WatchHistoryView()
            }
            .tabItem {
                Label("历史", systemImage: "clock.arrow.circlepath")
            }
            
            NavigationStack {
                SettingsScreen()
            }
            .tabItem {
                Label("设置", systemImage: "gearshape.fill")
            }
        }
    }
}
