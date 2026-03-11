import SwiftUI
import PhotosUI

struct UploadScreen: View {
    @State private var selectedItem: PhotosPickerItem?
    @State private var title = ""
    @State private var description = ""
    @State private var isUploading = false
    @State private var uploadComplete = false
    @State private var errorMessage: String?
    
    var body: some View {
        VStack(spacing: 24) {
            Text("上传视频").font(.title2).fontWeight(.semibold)
            
            if selectedItem == nil {
                PhotosPicker(selection: $selectedItem, matching: .videos) {
                    Label("从相册选择", systemImage: "photo.on.rectangle")
                }
                .buttonStyle(.bordered)
            } else {
                VStack(spacing: 16) {
                    TextField("标题", text: $title)
                        .textFieldStyle(.roundedBorder)
                    
                    TextField("描述", text: $description, axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(3...6)
                    
                    if isUploading {
                        ProgressView("上传中...")
                    } else if uploadComplete {
                        Label("上传完成！", systemImage: "checkmark.circle.fill")
                            .foregroundColor(.green)
                    } else {
                        HStack {
                            Button("取消") {
                                selectedItem = nil
                                title = ""
                                description = ""
                            }
                            .buttonStyle(.bordered)
                            
                            Button("开始上传") {
                                // Upload logic would go here
                                isUploading = true
                            }
                            .buttonStyle(.borderedProminent)
                        }
                    }
                    
                    if let error = errorMessage {
                        Text(error).foregroundColor(.red).font(.caption)
                    }
                }
                .padding(.horizontal, 32)
            }
            
            Spacer()
        }
        .padding(.top, 32)
    }
}
