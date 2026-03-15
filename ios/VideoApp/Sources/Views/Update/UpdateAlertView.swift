import SwiftUI

struct UpdateAlertModifier: ViewModifier {
    @ObservedObject var updateManager: UpdateCheckManager

    func body(content: Content) -> some View {
        content.alert("发现新版本", isPresented: $updateManager.showUpdateAlert) {
            Button("立即更新") { updateManager.openUpdate() }
            if !updateManager.isForceUpdate {
                Button("稍后再说", role: .cancel) { updateManager.dismiss() }
            }
        } message: {
            Text("版本 \(updateManager.versionName)\n\(updateManager.versionInfo)")
        }
    }
}
