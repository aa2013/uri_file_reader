import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

const kMethodChannelName = "uri_file_reader_method_channel";
const kGetFileInfoFromUri = "getFileInfoFromUri";
const kCopyFileFromUri = "copyFileFromUri";
const kReadFileAsBytesStream = "readFileAsBytesStream";

class UriFileInfo {
  final String fileName;
  final String? path;
  final int size;

  UriFileInfo({required this.fileName, required this.path, required this.size});
}

class UriFileReader {
  final _channel = const MethodChannel(kMethodChannelName);
  static final UriFileReader instance = UriFileReader._private();
  var _session = 0;

  UriFileReader._private();

  Future<UriFileInfo?> getFileInfoFromUri(String uri) async {
    if (!Platform.isAndroid) return Future.value(null);
    try {
      final result = await _channel.invokeMethod(kGetFileInfoFromUri, {"uri": uri});
      if (result == null) {
        return null;
      }
      return UriFileInfo(fileName: result["fileName"], path: result["path"], size: result["size"]);
    } catch (err, stack) {
      debugPrint(err.toString());
      debugPrintStack(stackTrace: stack);
      return null;
    }
  }

  /// [newFolderPath] The path of the new folder (must not end with '/' or '\')
  /// return new path
  Future<String?> copyFileFromUri(String uri, String newFolderPath) async {
    if (!Platform.isAndroid) return null;
    try {
      final path = await _channel.invokeMethod<String?>(kCopyFileFromUri, {"uri": uri, "newFolderPath": newFolderPath});
      if (path == null) {
        return null;
      }
      return path;
    } catch (err, stack) {
      debugPrint(err.toString());
      debugPrintStack(stackTrace: stack);
      return null;
    }
  }

  Future<Stream<Uint8List>?> readFileAsBytesStream(String uri) async {
    if (!Platform.isAndroid) return null;
    final eventChannelName = await _channel.invokeMethod<String?>(kReadFileAsBytesStream, {"uri": uri,"session": (++_session).toString()});
    if (eventChannelName == null) {
      debugPrint("eventChannelName is null");
      return null;
    }
    final eventChannel = EventChannel(eventChannelName);
    try {
      return eventChannel.receiveBroadcastStream().map((dynamic event) => event as Uint8List);
    } catch (err, stack) {
      debugPrint(err.toString());
      debugPrintStack(stackTrace: stack);
      return null;
    }
  }
}

final uriFileReader = UriFileReader.instance;
