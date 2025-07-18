import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// An implementation of [UriFileReaderPlatform] that uses method channels.
class MethodChannelUriFileReader {
  /// The method channel used to interact with the native platform.
  @visibleForTesting
  final methodChannel = const MethodChannel('uri_file_reader');

  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return version;
  }
}
