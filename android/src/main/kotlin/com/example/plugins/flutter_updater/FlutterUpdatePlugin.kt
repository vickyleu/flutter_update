package com.example.plugins.flutter_updater

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils
import androidx.core.content.FileProvider
import io.flutter.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipFile

class FlutterUpdatePlugin : FlutterPlugin, ActivityAware, MethodCallHandler {
    private var activityBinding: ActivityPluginBinding? = null
    private var channel: MethodChannel? = null
    private val listener: Callback = Callback()
    private var callBack: MethodCall? = null
    private var resultCallBack: MethodChannel.Result? = null
    override fun onAttachedToEngine(flutterPluginBinding: FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_updater")
        val channel = channel
        channel!!.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(plugin: FlutterPluginBinding) {
        val channel = channel
        channel?.setMethodCallHandler(null)
        callBack = null
        resultCallBack = null
        this.channel = null
    }

    override fun onDetachedFromActivityForConfigChanges() {
        val activityBinding = activityBinding
        activityBinding?.removeActivityResultListener((listener as ActivityResultListener))
        callBack = null
        resultCallBack = null
        this.activityBinding = null
    }

    override fun onAttachedToActivity(plugin: ActivityPluginBinding) {
        activityBinding = plugin
        val activityBinding = activityBinding
        activityBinding!!.addActivityResultListener((listener as ActivityResultListener))
    }

    override fun onReattachedToActivityForConfigChanges(plugin: ActivityPluginBinding) {
        onAttachedToActivity(plugin)
    }

    override fun onDetachedFromActivity() {
        onDetachedFromActivityForConfigChanges()
    }

    private fun _toMap(reason: String, flag: Int): Map<String, Any> {
        val map: MutableMap<String, Any> = HashMap()
        map["reason"] = reason
        map["flag"] = flag
        return map
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        val activityBinding = activityBinding
        if (activityBinding != null) {
            val activity = activityBinding.activity
            val method = call.method
            if (method != null) {
                if (method == "install") {
                    val path = call.argument<Any>("path") as String?
                    if (path == null) {
                        if (channel == null) return
                        channel!!.invokeMethod("failure", _toMap("文件为空", 1))
                        resultCall(result, "文件为空")
                        return
                    }
                    if (VERSION.SDK_INT >= 26) {
                        val canRequestPackageInstalls =
                            activity.packageManager.canRequestPackageInstalls()
                        if (canRequestPackageInstalls) {
                            installFromPath(activity, path, result)
                        } else {
                            resultCallBack = result
                            callBack = call
                            val packageURI = Uri.parse("package:" + activity.packageName)
                            val intent =
                                Intent("android.settings.MANAGE_UNKNOWN_APP_SOURCES", packageURI)
                            activity.startActivityForResult(intent, REQ_CODE)
                        }
                    } else {
                        try {
                            Log.wtf(
                                "INSTALL_NON_MARKET_APPS", "${
                                    Settings.Secure.getInt(
                                        activity.contentResolver,
                                        Settings.Secure.INSTALL_NON_MARKET_APPS
                                    ) != 1
                                }"
                            )
                            val isAppNotAllowed = Settings.Secure.getInt(
                                activity.contentResolver,
                                Settings.Secure.INSTALL_NON_MARKET_APPS
                            ) != 1
                            if (isAppNotAllowed) {
                                resultCallBack = result
                                callBack = call
                                val intent2 = Intent(Settings.ACTION_SECURITY_SETTINGS)
                                activity.startActivityForResult(intent2, REQ_UI_CODE)
                                return
                            }
                        } catch (e: SettingNotFoundException) {
                            e.printStackTrace()
                        }
                        installFromPath(activity, path, result)
                    }
                }
            }
        }
    }

    private fun installFromPath(activity: Activity, path: String?, result: MethodChannel.Result) {
        val file = File(path)
        if (!isAPK(file)) {
            if (channel == null) return
            channel!!.invokeMethod("failure", _toMap("安装包不合法", 4))
            resultCall(result, "安装包不合法")
        } else {
            val intent = Intent("android.intent.action.VIEW")
            if (VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri = FileProvider.getUriForFile(
                    (activity as Context),
                    activity.packageName + ".FileProvider",
                    file
                )
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                activity.grantUriPermission(
                    activity.packageName + ".FileProvider",
                    apkUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                activity.grantUriPermission(
                    activity.packageName + ".FileProvider",
                    apkUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                activity.grantUriPermission(
                    activity.packageName + ".FileProvider",
                    apkUri,
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            } else {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.setDataAndType(
                    Uri.parse("file://$file"),
                    "application/vnd.android.package-archive"
                )
            }
            if (channel == null) return
            channel!!.invokeMethod("success", "文件路径" + file.absolutePath)
            resultCall(result, "开始安装" + file.absolutePath)
            activity.startActivity(intent)
        }
    }

    private fun resultCall(result: MethodChannel.Result, message: String) {
        try {
            result.success(message)
        } catch (e: Exception) {
        }
    }

    fun isAPK(file: File): Boolean {
        val dexFile = "classes.dex"
        val manifestFile = "AndroidManifest.xml"
        var hasDex = false
        var hasManifest = false
        return try {
            val zipFile = ZipFile(file)
            val zipFiles = zipFile.entries()
            while (zipFiles.hasMoreElements() && (!hasDex || !hasManifest)) {
                val zEntry = zipFiles.nextElement()
                if (zEntry.name.equals(dexFile, ignoreCase = true)) {
                    hasDex = true
                } else if (zEntry.name.equals(manifestFile, ignoreCase = true)) {
                    hasManifest = true
                }
            }
            /*
//      below  android 6 throw "entry not named"
//
//      fis = new FileInputStream(file);
//      zipIs = new ZipInputStream(new BufferedInputStream(fis));
//
//      zEntry=zipIs.getNextEntry();
//      while (zEntry!=null&&(!hasDex || !hasManifest)){
//        if(zEntry.getName().equalsIgnoreCase(dexFile)){
//          hasDex = true;
//        }else  if(zEntry.getName().equalsIgnoreCase(manifestFile)){
//          hasManifest = true;
//        }
//        zEntry=zipIs.getNextEntry();
      }*/
            true
        } catch (var10: FileNotFoundException) {
            false
        } catch (var11: IOException) {
            false
        }
    }

    inner class Callback : ActivityResultListener {
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
            return if (REQ_CODE != requestCode  &&  REQ_UI_CODE != requestCode) {
                false
            } else {
                val result = resultCallBack
                if (result != null) {
                    val activityBinding = activityBinding
                    val callBack = callBack
                    if(REQ_CODE == requestCode){
                        if (resultCode != Activity.RESULT_OK) {
                            if (channel == null) return true
                            channel!!.invokeMethod("failure", _toMap("没有安装权限resultCode:$resultCode", 2))
                            resultCall(result, "没有安装权限resultCode:$resultCode")
                            return false
                        }
                    }else{
                        if(activityBinding!=null){
                            val isAppNotAllowed = Settings.Secure.getInt(
                                activityBinding.activity.contentResolver,
                                Settings.Secure.INSTALL_NON_MARKET_APPS
                            ) != 1
                            if(isAppNotAllowed){
                                if (channel == null) return true
                                channel!!.invokeMethod("failure", _toMap("没有安装权限", 2))
                                resultCall(result, "没有安装权限")
                                return false
                            }
                        }
                    }
                    if (activityBinding == null || callBack == null) {
                        if (channel == null) return true
                        channel!!.invokeMethod("failure", _toMap("页面已销毁", 3))
                        resultCall(result, "页面已销毁")
                    } else {
                        val path = callBack.argument<Any>("path") as String?
                        if (!TextUtils.isEmpty(path)) {
                            val activity = activityBinding.activity
                            installFromPath(activity, path, result)
                        } else {
                            if (channel == null) return true
                            channel!!.invokeMethod("failure", _toMap("文件为空", 1))
                            resultCall(result, "文件为空")
                        }
                    }
                }
                true
            }
        }
    }

    companion object {
        private const val REQ_CODE = 10671
        private const val REQ_UI_CODE = 10672
    }
}