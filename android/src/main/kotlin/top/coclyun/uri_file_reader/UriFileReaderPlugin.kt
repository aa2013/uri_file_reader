package top.coclyun.uri_file_reader

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
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
    
        val args: Map<String, Any> = call.arguments as? Map<String, Any> ?: mapOf()
        val uriString = args["uri"] as? String
        if (uriString == null) {
            result.error("INVALID_ARGUMENTS", "URI string is null", null)
            return
        }
    
        try {
            val uri = uriString.toUri()
            var fileName: String? = null
            var size: Long = -1L // 使用-1表示未知大小
    
            // 优先处理 content:// URI
            if ("content".equals(uri.scheme, ignoreCase = true)) {
                mainActivity!!.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        // 获取文件名
                        val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            fileName = cursor.getString(displayNameIndex)
                        }
    
                        // 获取文件大小
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } 
            // 兼容 file:// URI
            else if ("file".equals(uri.scheme, ignoreCase = true)) {
                val file = uri.path?.let { File(it) }
                if (file != null && file.exists()) {
                    fileName = file.name
                    size = file.length()
                }
            }
            
            // 尝试用 DocumentFile 作为后备方案
            if (fileName == null || size == -1L) {
                val documentFile = DocumentFile.fromSingleUri(mainActivity!!, uri);
                if (fileName == null) {
                    fileName = documentFile?.name
                }
                if (size == -1L) {
                    size = documentFile?.length() ?: -1L
                }
            }
    
            val filePath = getFilePathFromUri(mainActivity!!, uri)
    
            result.success(
                mapOf(
                    "fileName" to fileName,
                    "size" to size,
                    "path" to filePath
                )
            )
        } catch (e: Exception) {
            e.printStackTrace();
            result.success(null)
        }
    }


    /**
     * 根据 URI 获取文件的真实路径。
     */
    private fun getFilePathFromUri(context: Context, uri: Uri): String? {
        // 1. 判断是否是 DocumentProvider 的 URI
        if (DocumentsContract.isDocumentUri(context, uri)) {
            // A. ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                }
                // TODO: Handle other types of storage (e.g., SD cards) if necessary
            }
            // B. DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                 // 对于 "content://downloads/public_downloads" URI，需要特殊处理
                if (id.startsWith("raw:")) {
                    return id.replaceFirst("raw:", "");
                }
                
                return try {
                    val contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), id.toLong())
                    getDataColumn(context, contentUri, null, null)
                } catch (e: NumberFormatException) {
                    null
                }
            }
            // C. MediaProvider
            else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        }
        // 2. 判断是否是 content:// 协议的普通 URI (非 DocumentProvider)
        else if ("content".equals(uri.scheme, ignoreCase = true)) {
             // 如果是 Google Photos 的 URI，也返回 null，因为它不能直接访问
            return if (isGooglePhotosUri(uri)) null else getDataColumn(context, uri, null, null)
        }
        // 3. 判断是否是 file:// 协议的 URI
        else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    /**
     * 从 ContentResolver 查询 _data 列，即文件的真实路径
     */
    private fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null
        val column = MediaStore.MediaColumns.DATA // 使用 MediaStore.MediaColumns.DATA 更通用
        val projection = arrayOf(column)
        try {
            cursor = context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } catch (e: Exception) {
            e.printStackTrace() // 调试
        }
        finally {
            cursor?.close()
        }
        return null
    }

    // --- Helper Functions ---
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }
    
    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
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