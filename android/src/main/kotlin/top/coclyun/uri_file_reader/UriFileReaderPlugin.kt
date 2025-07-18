package top.coclyun.uri_file_reader

import android.app.Activity
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.URLDecoder
import kotlin.collections.mapOf

const val kEventChannelName = "uri_file_reader_event_channel";
const val kMethodChannelName = "uri_file_reader_method_channel";
const val getFileInfoFromUri = "getFileInfoFromUri";
const val copyFileFromUri = "copyFileFromUri";
const val tag = "UriFileReaderPlugin";

/** UriFileReaderPlugin */
class UriFileReaderPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var methodChannel: MethodChannel
    private lateinit var eventChannel: EventChannel
    var mainActivity: Activity? = null
    val hasActivity: Boolean
        get() = mainActivity != null

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        val binaryMessenger = flutterPluginBinding.binaryMessenger
        methodChannel = MethodChannel(binaryMessenger, kMethodChannelName)
        methodChannel.setMethodCallHandler(this)
        eventChannel = EventChannel(binaryMessenger, kEventChannelName)
        eventChannel.setStreamHandler(object : EventChannel.StreamHandler {
            val sinkMap: MutableMap<String, EventChannel.EventSink> = hashMapOf()
            override fun onListen(
                arguments: Any?,
                events: EventChannel.EventSink?,
            ) {
                if (!hasActivity) {
                    Log.d(tag, "mainActivity is null")
                    return
                }
                if (arguments is Map<*, *>) {
                    val uri = arguments["uri"] as? String
                    if (uri == null || events == null) {
                        mainActivity!!.runOnUiThread {
                            events?.error("1", "uri is empty.", null)
                        }
                        return
                    }
                    try {
                        sinkMap[uri] = events
                        startSendFileBytes2Flutter(uri, sinkMap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mainActivity!!.runOnUiThread {
                            events.error("1", "uri is empty.", null)
                        }
                    }
                }
            }

            override fun onCancel(arguments: Any?) {
                if (arguments is Map<*, *>) {
                    val uri = arguments["uri"] as? String
                    if (uri != null) {
                        sinkMap.remove(uri)
                    }
                }
            }

        })
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            getFileInfoFromUri -> getFileInfoFromUri(call, result)
            copyFileFromUri -> copyFileFromUri(call, result)
        }
    }

    private fun getFileInfoFromUri(call: MethodCall, result: Result) {
        if (!hasActivity) {
            result.success(null)
            return
        }
        var args: Map<String, Any> = mapOf()
        if (call.arguments is Map<*, *>) {
            args = call.arguments as Map<String, Any>
        }
        try {
            val content = args["uri"].toString()
            val uri = content.toUri()
            val documentFile = DocumentFile.fromSingleUri(mainActivity!!, uri)
            val fileName = URLDecoder.decode(documentFile!!.name, "UTF-8")
            val length = documentFile.length()
            val filePath = documentFile.uri.path
            result.success(
                mapOf(
                    "fileName" to fileName,
                    "size" to length,
                    "path" to filePath
                )
            )
        } catch (e: Exception) {
            e.printStackTrace();
            result.success(null)
        }
    }

    private fun copyFileFromUri(call: MethodCall, result: Result) {
        if (!hasActivity) {
            result.success(null)
            return
        }
        var args: Map<String, Any> = mapOf()
        if (call.arguments is Map<*, *>) {
            args = call.arguments as Map<String, Any>
        }
        var newPath: String? = null
        try {
            val content = args["uri"].toString()
            val uri = content.toUri();
            val documentFile = DocumentFile.fromSingleUri(mainActivity!!, uri)
            val fileName = documentFile!!.name
            newPath = args["newFolderPath"].toString() + "/${fileName}";
            val inputStream = mainActivity!!.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(tag, "Failed to open input stream for URI: $content")
                result.success(null)
                return
            }
            val destFile = File(newPath)
            val outputStream: OutputStream = FileOutputStream(destFile)
            val buffer = ByteArray(1024 * 10)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
            inputStream.close()
            outputStream.close()
            result.success(newPath)
        } catch (e: Exception) {
            e.printStackTrace();
            result.success(null)
            return
        }
    }

    private fun startSendFileBytes2Flutter(
        uriStr: String,
        sinkMap: MutableMap<String, EventChannel.EventSink>,
    ) {
        if (!hasActivity) {
            Log.d(tag, "mainActivity is null")
            return
        }
        val uri = uriStr.toUri();
        val inputStream = mainActivity!!.contentResolver.openInputStream(uri)
        if (inputStream == null) {
            if (sinkMap.contains(uriStr)) {
                mainActivity!!.runOnUiThread {
                    try {
                        sinkMap[uriStr]?.error("2", "inputStream is null", null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                sinkMap.remove(uriStr)
            }
            return
        }
        Thread {
            try {
                inputStream.use {
                    val buffer = ByteArray(1024 * 10)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        if (!sinkMap.contains(uriStr)) {
                            throw Exception("Not found sink for uri: $uriStr")
                        }
                        val data = buffer.copyOf(length)
                        if (!hasActivity) {
                            throw Exception("mainActivity is null")
                        }
                        mainActivity!!.runOnUiThread {
                            try {
                                sinkMap[uriStr]?.success(data)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainActivity?.runOnUiThread {
                    try {
                        sinkMap[uriStr]?.error("3", e.message, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                sinkMap.remove(uriStr)
            } finally {
                mainActivity?.runOnUiThread {
                    try {
                        try {
                            sinkMap[uriStr]?.endOfStream()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }.start()
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        methodChannel.setMethodCallHandler(null)
    }

    //region ActivityAware
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        mainActivity = binding.activity
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        mainActivity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        mainActivity = null
    }


    override fun onDetachedFromActivity() {
        mainActivity = null
    }
    //endregion
}
