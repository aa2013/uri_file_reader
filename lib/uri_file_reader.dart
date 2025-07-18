import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

const _kEventChannelName = "uri_file_reader_event_channel";
const _kMethodChannelName = "uri_file_reader_method_channel";
const _getFileInfoFromUri = "getFileInfoFromUri";
const _copyFileFromUri = "copyFileFromUri";

class UriFileInfo {
  final String fileName;
  final String? path;
  final int size;

  UriFileInfo({required this.fileName, required this.path, required this.size});
}

class UriFileReader {
  final _channel = const MethodChannel(_kMethodChannelName);
  static final UriFileReader instance = UriFileReader._private();

  UriFileReader._private();

  Future<UriFileInfo?> getFileInfoFromUri(String uri) async {
    if (!Platform.isAndroid) return Future.value(null);
    try {
      final result = await _channel.invokeMethod(_getFileInfoFromUri, {"uri": uri});
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
      final path = await _channel.invokeMethod<String?>(_copyFileFromUri, {"uri": uri, "newFolderPath": newFolderPath});
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

  Stream<Uint8List>? readFileAsBytesStream(String uri) {
    if (!Platform.isAndroid) return null;
    const eventChannel = EventChannel(_kEventChannelName);
    try {
      return eventChannel.receiveBroadcastStream({"uri": uri}).map((dynamic event) => event as Uint8List);
    } catch (err, stack) {
      debugPrint(err.toString());
      debugPrintStack(stackTrace: stack);
      return null;
    }
  }
}

final uriFileReader = UriFileReader.instance;
