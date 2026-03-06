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