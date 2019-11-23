# flutter_update

A  Flutter download plugin for android device,when download finished,plugin will automatic open archive,
permission should be handle by yourself,(file write/read .etc).

## Getting Started

This project is a starting point for a Flutter
[plug-in package](https://flutter.dev/developing-packages/),
a specialized package that includes platform-specific implementation code for
Android .

For help getting started with Flutter, view our
[online documentation](https://flutter.dev/docs), which offers tutorials,
samples, guidance on mobile development, and a full API reference.



### simple usage

implementation

[path_provider](https://pub.dev/packages/path_provider "Path_provider")

 or

define some else

```
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
```