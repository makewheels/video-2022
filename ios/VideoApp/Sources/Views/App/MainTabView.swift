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
                SettingsScreen()
            }
            .tabItem {
                Label("设置", systemImage: "gearshape.fill")
            }
        }
    }
}
