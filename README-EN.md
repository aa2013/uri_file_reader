# uri_file_reader

A library that supports reading files and retrieving basic file information directly from URIs, eliminating the need to create copies in private app directories - particularly useful for handling large files that may cause cache bloat.

> The English documentation was translated by [DeepSeek]([DeepSeek | 深度求索](https://www.deepseek.com/)).
---

[简体中文](./README.md) |English

---

## Platform Support

| Platform | Support  |
| -------- |:-------:|
| Android  | ✔️      |
| iOS      | ✖️      |

## Quick Start

### Usage

```dart
import 'package:uri_file_reader/uri_file_reader.dart';

final uri = "content://...";

// Retrieve basic file information from URI
final fileInfo = await uriFileReader.getFileInfoFromUri(uri);
final fileName = fileInfo?.fileName;
final filePath = fileInfo?.path;
final size = fileInfo?.size;

// Read file bytes as stream
final stream = await uriFileReader.readFileAsBytesStream(uri);
if (stream == null) {
  print("Read operation failed!");
} else {
    stream.listen((bytes) {
      // Process byte data
    });
}

// Copy file from URI to another path
// Note: New folder path must NOT end with '/' or '\'
final newPath = uriFileReader.copyFileFromUri(uri, newFolderPath);
```