import SwiftUI

@MainActor
class UpdateCheckManager: ObservableObject {
    static let shared = UpdateCheckManager()

    @Published var showUpdateAlert = false
    @Published var isForceUpdate = false
    @Published var versionName: String = ""
    @Published var versionInfo: String = ""
    @Published var downloadUrl: String = ""

    private let api = APIClient.shared

    func checkForUpdate() async {
        let currentVersionCode = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String
        let versionCode = Int(currentVersionCode ?? "1") ?? 1

        do {
            let response: CheckUpdateResponse = try await api.get("/app/checkUpdate?platform=ios&versionCode=\(versionCode)")
            if response.hasUpdate == true {
                self.showUpdateAlert = true
                self.isForceUpdate = response.isForceUpdate ?? false
                self.versionName = response.versionName ?? ""
                self.versionInfo = response.versionInfo ?? ""
                self.downloadUrl = response.downloadUrl ?? ""
            }
        } catch {
            print("Update check failed: \(error)")
        }
    }

    func openUpdate() {
        if let url = URL(string: downloadUrl) {
            UIApplication.shared.open(url)
        }
    }

    func dismiss() {
        if !isForceUpdate {
            showUpdateAlert = false
        }
    }
}
