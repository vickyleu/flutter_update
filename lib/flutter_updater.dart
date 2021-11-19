import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

import 'DownLoadManage.dart';

typedef void SuccessBlock(FlutterUpdater updater, String reason);
typedef void ProgressBlock(int receiveProgress, int total);
typedef void FailureBlock(FlutterUpdater updater, String? reason, int? flag);

class FlutterUpdater {
  MethodChannel _channel = const MethodChannel('flutter_updater');

  ///私有构造方法
  FlutterUpdater._() {
    _channel.setMethodCallHandler((call) async{
      switch (call.method) {
        case "failure":
          String? reason = call.arguments["reason"];
          int? flag = call.arguments["flag"];
          _failure(reason, flag);
          break;
        case "success":
          String result = (call.arguments as List).first;
          _success(result);
          break;
      }
    });
  }

  ///单实例
  static FlutterUpdater? _instance;

  static FlutterUpdater instance() {
    if (_instance == null) {
      _instance = FlutterUpdater._();
    }
    return _instance!;
  }

  SuccessBlock? _successBlock;
  ProgressBlock? _progressBlock;
  FailureBlock? _failureBlock;

  ///失败的回调
  _failure(String? failure, int? flag) {
    _failureBlock!(this, failure, flag);
  }

  ///成功的回调
  _success(String result) {
    _successBlock!(this, result);
  }

  ///注册回调
  void registerCallback(
      {SuccessBlock? successBlock,
      ProgressBlock? progressBlock,
      FailureBlock? failureBlock}) {
    _successBlock = successBlock;
    _progressBlock = progressBlock;
    _failureBlock = failureBlock;
  }

  ///释放回调
  void dispose() {
    _successBlock = null;
    _progressBlock = null;
    _failureBlock = null;
  }

  ///发送命令给原生,执行安装
  Future<dynamic> install(String path) async {
    await _channel.invokeMethod("install", {"path": path});
  }

  ///使用dart做文件下载处理
  Future<void> download(
      {required String url,
      bool? forceCover,
      required String savePath,
      Function(dynamic,bool flag)? callback}) async {
    if (Platform.isAndroid) {
      final extension = p.extension(url);
      final fileName = p.basenameWithoutExtension(url);
      var fileRealPath = "$savePath/$fileName.$extension";
      File file = File(savePath);
      if (!await file.exists()) {
        new Directory(savePath).createSync();
      }

      if (forceCover!) {
        File file = File(fileRealPath);
        if (await file.exists()) {
          await file.delete();
        }
      }
      await DownLoadManage().download(url, fileRealPath,
          onReceiveProgress: (received, total) {
        if (total != -1) {
          print("下载1已接收：" +
              received.toString() +
              "总共：" +
              total.toString() +
              "进度：+${(received / total * 100).floor()}%");
          _progressBlock!((received / total * 100).floor(), total);
        }
      }, done: () async {
        print("下载1完成");
        callback?.call(await install(fileRealPath),true);
      }, failed: (e) {
        print("下载1失败：" + e.toString());
        callback?.call("下载失败",false);
      });
    }
  }
}
