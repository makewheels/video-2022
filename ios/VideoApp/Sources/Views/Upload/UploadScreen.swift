import SwiftUI
import PhotosUI

struct UploadScreen: View {
    @State private var selectedItem: PhotosPickerItem?
    @State private var title = ""
    @State private var description = ""
    @State private var category = ""
    @State private var tags: [String] = []
    @State private var tagInput = ""
    @State private var isUploading = false
    @State private var uploadComplete = false
    @State private var errorMessage: String?
    
    private let categoryOptions = [
        "音乐", "游戏", "教育", "科技", "生活",
        "娱乐", "新闻", "体育", "动漫", "美食",
        "旅行", "知识", "影视", "搞笑", "其他"
    ]
    
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
                    
                    Picker("分类", selection: $category) {
                        Text("选择分类").tag("")
                        ForEach(categoryOptions, id: \.self) { Text($0).tag($0) }
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                        if !tags.isEmpty {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    ForEach(tags, id: \.self) { tag in
                                        HStack(spacing: 4) {
                                            Text(tag).font(.caption)
                                            Button(action: { tags.removeAll { $0 == tag } }) {
                                                Image(systemName: "xmark.circle.fill")
                                                    .font(.caption)
                                            }
                                        }
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.secondary.opacity(0.2))
                                        .cornerRadius(12)
                                    }
                                }
                            }
                        }
                        TextField("输入标签后按回车添加", text: $tagInput)
                            .textFieldStyle(.roundedBorder)
                            .onSubmit {
                                let trimmed = tagInput.trimmingCharacters(in: .whitespaces)
                                if !trimmed.isEmpty && !tags.contains(trimmed) {
                                    tags.append(trimmed)
                                }
                                tagInput = ""
                            }
                    }
                    
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
                                category = ""
                                tags = []
                                tagInput = ""
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
