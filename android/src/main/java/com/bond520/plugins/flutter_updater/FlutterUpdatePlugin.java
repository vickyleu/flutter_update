package com.bond520.plugins.flutter_updater;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FlutterUpdatePlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {
  private ActivityPluginBinding activityBinding;
  private MethodChannel channel;
  private final FlutterUpdatePlugin.Callback listener = new FlutterUpdatePlugin.Callback();
  private MethodCall callBack;
  private Result resultCallBack;
  private static final int REQ_CODE = 10671;

  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    this.channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_updater");
    MethodChannel channel = this.channel;
      channel.setMethodCallHandler(this);

  }

  public void onDetachedFromEngine(@NonNull FlutterPluginBinding plugin) {
    MethodChannel channel = this.channel;
    if (channel != null) {
      channel.setMethodCallHandler(null);
    }

    this.callBack = (MethodCall)null;
    this.resultCallBack = (Result)null;
    this.channel = (MethodChannel)null;
  }

  public void onDetachedFromActivityForConfigChanges() {
    ActivityPluginBinding activityBinding = this.activityBinding;
    if (activityBinding != null) {
      activityBinding.removeActivityResultListener((ActivityResultListener)this.listener);
    }

    this.callBack = (MethodCall)null;
    this.resultCallBack = (Result)null;
    this.activityBinding = (ActivityPluginBinding)null;
  }

  public void onAttachedToActivity(@NonNull ActivityPluginBinding plugin) {
    this.activityBinding = plugin;
    ActivityPluginBinding activityBinding = this.activityBinding;
      activityBinding.addActivityResultListener((ActivityResultListener)this.listener);
  }

  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding plugin) {
    this.onAttachedToActivity(plugin);
  }

  public void onDetachedFromActivity() {
    this.onDetachedFromActivityForConfigChanges();
  }


  private Map<String,Object> _toMap(String reason,int flag){
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("reason",reason);
    map.put("flag",flag);
    return map;
  }

  public void onMethodCall(@NonNull MethodCall call, @NonNull final Result result) {
    ActivityPluginBinding activityBinding = this.activityBinding;
    if (activityBinding != null) {
      Activity activity = activityBinding.getActivity();
        String method = call.method;
        if (method != null) {
          if (method.equals("install")) {
                String path = (String)call.argument("path");
                if (path == null) {
                  if(channel==null)return;
                  channel.invokeMethod("failure", _toMap("文件为空",1));
                  result.success("文件为空");
                  return;
                }
                if (VERSION.SDK_INT >= 26) {
                  boolean canRequestPackageInstalls = activity.getPackageManager().canRequestPackageInstalls();
                  if (canRequestPackageInstalls) {
                    this.installFromPath(activity, path, result);
                  } else {
                    this.resultCallBack = result;
                    this.callBack = call;
                    Uri packageURI = Uri.parse("package:" + activity.getPackageName());
                    Intent intent = new Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", packageURI);
                    activity.startActivityForResult(intent, REQ_CODE);
                  }
                } else {
                  this.installFromPath(activity, path, result);
                }
              }
        }
    }
  }

  private void installFromPath(Activity activity, String path, Result result) {
    File file = new File(path);
    if (!this.isAPK(file)) {
      if(channel==null)return;
      channel.invokeMethod("failure", _toMap("安装包不合法",4));
      result.success("安装包不合法");
    } else {
      Intent intent = new Intent("android.intent.action.VIEW");
      if (VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Uri apkUri = FileProvider.getUriForFile((Context)activity, activity.getPackageName() + ".FileProvider", file);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.grantUriPermission(activity.getPackageName() + ".FileProvider", apkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.grantUriPermission(activity.getPackageName() + ".FileProvider", apkUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        activity.grantUriPermission(activity.getPackageName() + ".FileProvider", apkUri, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
      } else {
        intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setDataAndType(Uri.parse("file://" + file), "application/vnd.android.package-archive");
      }
      if(channel==null)return;
      channel.invokeMethod("success","文件路径"+file.getAbsolutePath());
      result.success("开始安装"+file.getAbsolutePath());
      activity.startActivity(intent);
    }
  }

  public final boolean isAPK(@NonNull File file) {
    FileInputStream fis = null;
    ZipInputStream zipIs = null;
    ZipEntry zEntry = null;
    String dexFile = "classes.dex";
    String manifestFile = "AndroidManifest.xml";
    boolean hasDex = false;
    boolean hasManifest = false;
    try {
      fis = new FileInputStream(file);
      zipIs = new ZipInputStream(new BufferedInputStream(fis));

      zEntry=zipIs.getNextEntry();
      while (zEntry!=null&&(!hasDex || !hasManifest)){
        if(zEntry.getName().equalsIgnoreCase(dexFile)){
          hasDex = true;
        }else  if(zEntry.getName().equalsIgnoreCase(manifestFile)){
          hasManifest = true;
        }
        zEntry=zipIs.getNextEntry();
      }

      zipIs.close();
      fis.close();
      return true;
    } catch (FileNotFoundException var10) {
      return false;
    } catch (IOException var11) {
      return false;
    }
  }

  public final class Callback implements ActivityResultListener {
    public boolean onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
      if (REQ_CODE != requestCode) {
        return false;
      } else {
        Result result = FlutterUpdatePlugin.this.resultCallBack;
        if (result != null) {
          ActivityPluginBinding activityBinding = FlutterUpdatePlugin.this.activityBinding;
          MethodCall callBack = FlutterUpdatePlugin.this.callBack;
          if (resultCode == -1) {
            if(channel==null)return true;
            channel.invokeMethod("failure", _toMap("没有安装权限",2));
            result.success("没有安装权限");
          }
          if(activityBinding==null||callBack==null){
            if(channel==null)return true;
            channel.invokeMethod("failure", _toMap("页面已销毁",3));
            result.success("页面已销毁");
          }else  {
            String path = (String) callBack.argument("path");
            if(!TextUtils.isEmpty(path)){
              Activity activity = activityBinding.getActivity();
              installFromPath(activity, path, result);
            }else {
              if(channel==null)return true;
              channel.invokeMethod("failure", _toMap("文件为空",1));
              result.success("文件为空");
            }
          }
        }
        return  true;
      }
    }
  }

}
