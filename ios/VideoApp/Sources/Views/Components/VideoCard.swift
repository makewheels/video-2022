import SwiftUI

struct VideoCard: View {
    let video: VideoItem
    
    var body: some View {
        HStack(spacing: 12) {
            AsyncImage(url: URL(string: video.coverUrl ?? "")) { image in
                image.resizable().aspectRatio(16/9, contentMode: .fill)
            } placeholder: {
                Rectangle().fill(Color.gray.opacity(0.3))
            }
            .frame(width: 120, height: 68)
            .cornerRadius(8)
            .clipped()
            
            VStack(alignment: .leading, spacing: 4) {
                Text(video.title ?? "无标题")
                    .font(.subheadline)
                    .lineLimit(2)
                
                HStack {
                    if let count = video.watchCount {
                        Text("\(count) 次观看")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    if let time = video.createTimeString {
                        Text(time)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                
                if let duration = video.duration {
                    Text(formatDuration(duration))
                        .font(.caption2)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.black.opacity(0.6))
                        .foregroundColor(.white)
                        .cornerRadius(4)
                }
            }
        }
        .padding(.vertical, 4)
    }
    
    private func formatDuration(_ ms: Int) -> String {
        let totalSeconds = ms / 1000
        let m = totalSeconds / 60
        let s = totalSeconds % 60
        return String(format: "%d:%02d", m, s)
    }
}
