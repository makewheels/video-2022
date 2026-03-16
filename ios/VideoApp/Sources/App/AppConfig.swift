import Foundation

enum AppConfig {
    #if DEBUG
    static let apiBaseURL = "http://localhost:5022"
    #else
    static let apiBaseURL = "https://oneclick.video"
    #endif
    
    static let youtubeBaseURL = "https://youtube.videoplus.top:5030"

    #if DEBUG
    static let webBaseURL = "http://localhost:5173"
    #else
    static let webBaseURL = "https://oneclick.video"
    #endif
}
