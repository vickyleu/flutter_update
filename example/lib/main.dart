import 'dart:io';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_updater/flutter_updater.dart';
import 'package:path_provider/path_provider.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';

  @override
  void initState() {
    super.initState();
  }

  @override
  Widget build(BuildContext context) {
    return CupertinoApp(
      theme: CupertinoThemeData(
        primaryColor: Colors.blue,
      ),
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: EmptyPage(),
        ),
      ),
      localizationsDelegates: const <LocalizationsDelegate<dynamic>>[
        DefaultMaterialLocalizations.delegate,
        DefaultWidgetsLocalizations.delegate,
      ],
    );
  }
}

class EmptyPage extends StatefulWidget {
  @override
  State<StatefulWidget> createState() {
    return _EmptyPage();
  }
}

class _EmptyPage extends State<EmptyPage> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        child: Text(""),
      ),
    );
  }

  @override
  void initState() {
    super.initState();
    _download();
  }

  Future _download() async {
    final Directory directory = await getTemporaryDirectory();
    FlutterUpdater.instance()
      ..registerCallback(
          successBlock: (updater, result) {
            print("成功啦");
            updater.dispose();
            return;
          },
          progressBlock: (receiveProgress, total) {},
          failureBlock: (updater, reason, flag) {
            print("失败啦$reason");
            updater.dispose();
            return;
          })
      ..download(
          url: "http://download.dcloud.net.cn/HBuilder.9.0.2.macosx_64.dmg",
          savePath: "${directory.path}/xxx/",
          callback: (f) {
            print("ooooobbbbbjjj===>>$f");
          });
  }
}
