# uri_file_reader

支持 Flutter应用 从 URI 读取文件和获取一些文件的基础信息，避免复制一份到应用私有目录导致的缓存增长，特别是一些大文件

---

简体中文 | [English](./README-EN.md)

---

## 平台支持

| 平台    | 支持  |
| ------- |:----|
| Android | ✔️  |
| IOS     | ✖️  |

## 快速开始

### 用法

```dart
import 'package:uri_file_reader/uri_file_reader.dart';

final uri = "content://...";

//从URI中获取文件基础信息
final fileInfo = await uriFileReader.getFileInfoFromUri(uri);
final fileName = fileInfo?.fileName;
final filePath = fileInfo?.path;
final size = fileInfo?.size;

// 以流读取文件字节
final stream = await uriFileReader.readFileAsBytesStream(uri);
if (stream == null) {
  print("read file failed!");
}else{
    stream.listen((bytes){
    });
}

//从uri复制文件到另一个路径
//新的文件夹路径不能以 '/' 或 '\'结尾
final newPath = uriFileReader.copyFileFromUri(uri, newFolderPath)

```