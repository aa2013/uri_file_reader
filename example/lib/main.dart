import 'dart:io';

import 'package:fast_file_picker/fast_file_picker.dart';
import 'package:flutter/material.dart';
import 'dart:async';
import 'package:share_handler/share_handler.dart';
import 'package:uri_file_reader/uri_file_reader.dart';

void main() {
  runApp(const MyApp());
}

extension IntExt on int {
  String get sizeStr {
    if (this < 0) {
      return '-';
    }
    const kb = 1024;
    const mb = kb * 1024;
    const gb = mb * 1024;

    if (this >= gb) {
      return '${(this / gb).toStringAsFixed(2)} GB';
    } else if (this >= mb) {
      return '${(this / mb).toStringAsFixed(2)} MB';
    } else if (this >= kb) {
      return '${(this / kb).toStringAsFixed(2)} KB';
    } else {
      return '$this B';
    }
  }
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final textController = TextEditingController();
  UriFileInfo? uriFileInfo;
  StreamSubscription<SharedMedia>? shareHandlerStream;

  @override
  void initState() {
    super.initState();
    initShareHandler();
  }

  Future<void> initShareHandler() async {
    if (!Platform.isAndroid) {
      return;
    }
    final handler = ShareHandlerPlatform.instance;
    shareHandlerStream = handler.sharedMediaStream.listen((SharedMedia media) async {
      debugPrint("ShareMedia: ${media.attachments}, content: ${media.content}");
      if (media.content != null) {
        debugPrint("share content: ${media.content}");
        final fileInfo = await uriFileReader.getFileInfoFromUri(media.content!);
        setState(() {
          uriFileInfo = fileInfo;
        });
        if (fileInfo == null) {
          debugPrint("Cannot get file info from share content");
          return;
        }
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    // textController.text="content://com.android.chrome.FileProvider/images/screenshot/17540587435124097869892818155541.jpg";
    textController.text = "content://com.tencent.mm.external.fileprovider/attachment/2024%E6%BF%80%E6%B4%BB%E5%B7%A5%E5%85%B7.zip";
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: const Text('UriFileReader example app')),
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text("filename: ${uriFileInfo?.fileName ?? ""}"),
            Text("path: ${uriFileInfo?.path ?? ""}"),
            Text("size: ${uriFileInfo?.size.sizeStr ?? ""}"),

            TextField(controller: textController, decoration: InputDecoration(hintText: "Uri...")),
            TextButton(
              onPressed: () async {
                final fileInfo = await uriFileReader.getFileInfoFromUri(textController.text);
                setState(() {
                  uriFileInfo = fileInfo;
                });
              },
              child: Text("Get File Info"),
            ),

            TextButton(
              onPressed: () async {
                final file = await FastFilePicker.pickFile();
                if (file != null) {
                  debugPrint("path:${file.path} uri:${file.uri}");
                  if (file.uri != null) {
                    textController.text = file.uri!;
                  } else if (file.path != null) {
                    textController.text = "file://" + file.path!;
                  }
                } else {
                  debugPrint("file is null");
                }
              },
              child: Text("Use File Picker"),
            ),
          ],
        ),
      ),
    );
  }
}
