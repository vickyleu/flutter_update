import 'dart:async';
import 'dart:io';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'DownLoadManage.dart';

typedef void SuccessBlock(FlutterUpdater updater,String reason);
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
          String result= (call.arguments as List).first;
          _success(result);
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
  _success(String result){
    _successBlock(this,result);
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

   Future<void> download({@required String url,@required String savePath,Function(dynamic) callback}) async{
    if(Platform.isAndroid){
      final extension=p.extension(url);
      final fileName=p.basenameWithoutExtension(url);
      var file="$savePath/$fileName.$extension";
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
