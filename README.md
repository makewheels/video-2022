# 关键逻辑

## 上传

```mermaid
sequenceDiagram
    up主->>+后端: 获取上传凭证
    后端-->>后端: 向阿里云STS请求AssumeRole
    后端->>up主: 返回STS凭证
    up主->>阿里云OSS: 直传对象存储
    up主->>后端: 通知上传完成
    后端->>-阿里云OSS: 获取源视频文件信息
    阿里云OSS->>后端: 返回源视频文件信息
    后端-->>+后端: 通过云函数 / 阿里云MPS转码视频
    后端->>阿里云OSS: 获取转码m3u8和ts文件信息
    阿里云OSS->>后端: 返回转码文件信息
    后端->>-后端: 解析HLS保存数据库
    观众->>后端: 获取HLS播放信息
    后端->>观众: 返回 ts地址 301重定向到OSS
    观众->>阿里云OSS: 前端播放器直接从OSS下载ts
    观众->>观众: 前端播放器播放
```

# 接口文档

[![Run in Postman](https://run.pstmn.io/button.svg)](https://app.getpostman.com/run-collection/dced8657344813ee3fbc?action=collection%2Fimport)

[用户接口](docs/api/user/api-user.md)

[上传视频接口](docs/api/video/api-video.md)

[YouTube接口](docs/api/youtube/api-youtube.md)

[播放视频接口](docs/api/play/api-play.md)

[转码接口](docs/api/transcode/api-transcode.md)

[统计接口](docs/api/statistics/api-statistics.md)

[AndroidApp接口](docs/api/app/api-app.md)

# MongoDB 表结构

[video 视频对象](docs/mongodb/video.md)

# 如何部署？

RSA

Redis

application.props

# Java 8 Stream Api Examples
[Java 8 Stream Api Examples](docs/java8-stream-examples/java8-stream-examples.md)

# 变更日志
[变更日志](docs/changes/changes.md)

# 运维
## 从file迁移到tsFile，条件是type=TRANSCODE_TS
```js
const query = { type: 'TRANSCODE_TS' };
const batchSize = 500;

let count = db.file.countDocuments(query);

print(`Total documents to migrate: ${count}`);

let offset = 0;
let progress = 0;
while (offset < count) {
  const docs = db.file.find(query).skip(offset).limit(batchSize).toArray();
  if (docs.length === 0) {
    print('No documents found to migrate.');
    break;
  }
  print(`Migrating ${docs.length} documents...`);

  for (const doc of docs) {
    db.tsFile.updateOne({ _id: doc._id }, { $set: doc }, { upsert: true });
    printjson(doc);
    progress++;
    const percentage = Math.floor(progress / count * 100);
    print(`Progress: ${progress}/${count} (${percentage}%)`);
  }

  offset += docs.length;

  db.file.deleteMany({ _id: { $in: docs.map(doc => doc._id) } });
}

print('Migration complete. Old documents deleted.');

```
## 改字段名
```js
db.tsFile.updateMany({}, { $rename: { 'type': 'fileType' } });

```

# YouTube
[YouTube下载](docs/design/youtube/youtube.md)