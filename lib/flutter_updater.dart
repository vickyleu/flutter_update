import 'dart:async';
import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';

import 'DownLoadManage.dart';

typedef void SuccessBlock(FlutterUpdater updater);
typedef void FailureBlock(FlutterUpdater updater,String reason,int flag);

class FlutterUpdater {
  MethodChannel _channel = const MethodChannel('flutter_updater');

  FlutterUpdater._(){
    _channel.setMethodCallHandler((call){
      switch(call?.method??""){
        case "failure":
         String reason= call.arguments["reason"];
         int flag= call.arguments["flag"];
         _failure(reason,flag);
          break;
        case "success":
          _success();
          break;
      }
      return;
    });
  }

  static FlutterUpdater _instance;
  static FlutterUpdater instance(){
    if(_instance==null){
      _instance=FlutterUpdater._();
    }
    return _instance;
  }

  SuccessBlock _successBlock;
  FailureBlock _failureBlock;

  _failure(String failure,int flag){
    _failureBlock(this,failure,flag);
  }
  _success(){
    _successBlock(this);
  }

  void registerCallback(SuccessBlock successBlock,FailureBlock failureBlock){
    _successBlock=successBlock;
    _failureBlock=failureBlock;
  }

  void dispose(){
    _successBlock=null;
    _failureBlock=null;
  }



   Future<dynamic> install(String path) async {
    await _channel.invokeMethod("install", {"path": path});
  }

   Future<void> download({@required String url,@required String savePath,@required String fileName,Function(dynamic) callback}) async{
    if(Platform.isAndroid){
      var file="$savePath/$fileName";
      File f = File(savePath);
      if (!await f.exists()) {
        new Directory(savePath).createSync();
      }
      await DownLoadManage().download(url,file ,
          onReceiveProgress: (received, total) {
            if (total != -1) {
              print("下载1已接收：" +
                  received.toString() +
                  "总共：" +
                  total.toString() +
                  "进度：+${(received / total * 100).floor()}%");
            }
          }, done: () async {
            print("下载1完成");
            callback(await install(file));
          }, failed: (e) {
            print("下载1失败：" + e.toString());
          });
    }
  }
}
