import 'dart:async';
import 'dart:io';

import 'package:dio/dio.dart';

/// 文件下载  懒加载单例
class DownLoadManage {
  ///用于记录正在下载的url，避免重复下载
///  var downloadingUrls = new List();
  var downloadingUrls = new Map<String, CancelToken>();

  /// 单例公开访问点
  factory DownLoadManage() => _getInstance()!;

  /// 静态私有成员，没有初始化
  static DownLoadManage? _instance;

  /// 私有构造函数
  DownLoadManage._() {
    /// 具体初始化代码
  }

  /// 静态、同步、私有访问点
  static DownLoadManage? _getInstance() {
    if (_instance == null) {
      _instance = DownLoadManage._();
    }
    return _instance;
  }

  void showDownloadProgress(received, total) {
    if (total != -1) {
      print((received / total * 100).toStringAsFixed(0) + "%");
    }
  }

  ///下载
  Future download(url, savePath,
      {ProgressCallback? onReceiveProgress,
      Function? done,
      Function? failed}) async {
    int downloadStart = 0;
    bool fileExists = false;
    File f = File(savePath);
    if (await f.exists()) {
      downloadStart = f.lengthSync();
      fileExists = true;
    }
    print("开始：" + downloadStart.toString());
    if (fileExists && downloadingUrls.containsKey(url)) {
      ///正在下载
      return;
    }
    var dio = Dio();
    int contentLength = await (_getContentLength(dio, url) as FutureOr<int>);
    if (downloadStart == contentLength) {
      ///存在本地文件，命中缓存
      done!();
      return;
    }
    CancelToken cancelToken = new CancelToken();
    downloadingUrls[url] = cancelToken;

    ///使用Dio下载apk
    Future downloadByDio(String url, int start) async {
      try {
        Response response = await dio.get(
          url,
          options: Options(
            responseType: ResponseType.stream,
            followRedirects: false,
            headers: {"range": "bytes=$start-"},
          ),
        );
        print(response.headers);
        File file = new File(savePath.toString());

        var raf = file.openSync(mode: FileMode.append);
        Completer completer = new Completer<Response>();
        Future future = completer.future;

        int received = start;
        int total = int.parse(response.headers["Content-Length"]!.first);
        Stream<List<int>> stream = response.data.stream;
        late StreamSubscription subscription;
        Future? asyncWrite;
        subscription = stream.listen(
          (data) {
            subscription.pause();
            // Write file asynchronously
            asyncWrite = raf.writeFrom(data).then((_raf) {
              // Notify progress
              received += data.length;
              if (onReceiveProgress != null) {
                onReceiveProgress(received, total);
              }
              raf = _raf;
              if (cancelToken == null || !cancelToken.isCancelled) {
                subscription.resume();
              }
            });
          },
          onDone: () async {
            try {
              await asyncWrite;
              await raf.close();
              completer.complete(response);
              downloadingUrls.remove(url);
              if (done != null) {
                done();
              }
            } catch (e) {
              downloadingUrls.remove(url);
              completer.completeError(_assureDioError(e));
              if (failed != null) {
                failed(e);
              }
            }
          },
          onError: (e) async {
            try {
              await asyncWrite;
              await raf.close();
              downloadingUrls.remove(url);
              if (failed != null) {
                failed(e);
              }
            } finally {
              completer.completeError(_assureDioError(e));
            }
          },
          cancelOnError: true,
        );
        // ignore: unawaited_futures
        cancelToken.whenCancel.then((_) async {
          await subscription.cancel();
          await asyncWrite;
          await raf.close();
        });

        return await _listenCancelForAsyncTask(cancelToken, future);
      } catch (e) {
        if(e is DioError){
          // The request was made and the server responded with a status code
          // that falls out of the range of 2xx and is also not 304.
          if (e.response != null) {
            print(e.response!.data);
            print(e.response!.headers);
            print(e.response!.requestOptions);
          } else {
            // Something happened in setting up or sending the request that triggered an Error
            print(e.requestOptions);
            print(e.message);
          }
          if (CancelToken.isCancel(e)) {
            print("下载取消");
          } else {
            if (failed != null) {
              failed(e);
            }
          }
        }else{
          if (failed != null) {
            failed(e);
          }
        }
        downloadingUrls.remove(url);
      }
    }

    await downloadByDio(url, downloadStart);
  }

  ///获取下载的文件大小
  Future _getContentLength(Dio dio, url) async {
    try {
      Response response = await dio.head(url);
      return int.parse(response.headers["Content-Length"]!.first);
    } catch (e) {
      print("_getContentLength Failed:" + e.toString());
      return 0;
    }
  }

  ///停止下载
  void stop(String url) {
    if (downloadingUrls.containsKey(url)) {
      downloadingUrls[url]!.cancel();
    }
  }

  ///监听取消的异步任务
  Future _listenCancelForAsyncTask(CancelToken cancelToken, Future future) {
    if (cancelToken != null && cancelToken.cancelError == null) {
      return Future.any([cancelToken.whenCancel, future]).then((result) {
        return result;
      }).catchError((e) {
        throw e;
      });
    } else {
      return future;
    }
  }

  ///处理异常的转换
  DioError _assureDioError(err) {
    if (err is DioError) {
      return err;
    } else {
      var _err = DioError(error: err, requestOptions: RequestOptions(path: "/"));
      return _err;
    }
  }
}
