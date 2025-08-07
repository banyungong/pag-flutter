package com.example.flutter_pag_plugin.platfromview

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import com.example.flutter_pag_plugin.DataLoadHelper
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import org.libpag.PAGFile
import org.libpag.PAGView
import org.libpag.PAGView.PAGViewListener
import java.io.IOException

class PlatformPagView(
    private val context: Context,
    private val binaryMessenger: BinaryMessenger,
    private val viewId: Int,
    private val creationParams: Any?
) : PlatformView, PAGViewListener {

    private var pagView: PAGView? = null
    private var containerView: RelativeLayout? = null
    private var methodChannel: MethodChannel? = null
    private var pagFile: PAGFile? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // 常量定义（与主插件保持一致）
    companion object {
        const val _nativeStart = "start"
        const val _nativeStop = "stop"
        const val _nativePause = "pause"
        const val _nativeSetProgress = "setProgress"
        const val _nativeGetPointLayer = "getLayersUnderPoint"

        const val _argumentAssetName = "assetName"
        const val _argumentPackage = "package"
        const val _argumentUrl = "url"
        const val _argumentFilePath = "filePath"
        const val _argumentBytes = "bytesData"
        const val _argumentRepeatCount = "repeatCount"
        const val _argumentInitProgress = "initProgress"
        const val _argumentAutoPlay = "autoPlay"
        const val _argumentPointX = "x"
        const val _argumentPointY = "y"
        const val _argumentProgress = "progress"

        const val _playCallback = "PAGCallback"
        const val _eventStart = "onAnimationStart"
        const val _eventEnd = "onAnimationEnd"
        const val _eventCancel = "onAnimationCancel"
        const val _eventRepeat = "onAnimationRepeat"
        const val _eventUpdate = "onAnimationUpdate"
    }

    init {
        // 设置方法通道
        methodChannel = MethodChannel(binaryMessenger, "flutter_pag_platform_view_$viewId")
        methodChannel?.setMethodCallHandler { call, result ->
            handleMethodCall(call, result)
        }

        // 创建容器视图
        containerView = RelativeLayout(context)

        // 解析创建参数并初始化PAG
        parseCreationParams()
    }

    override fun getView(): View? {
        Log.e("Pag Test","getView:${viewId}")
        return containerView
    }

    override fun dispose() {
        Log.e("Pag Test","dispose:${viewId}")
        
        // 在主线程中安全释放
        mainHandler.post {
            pagView?.stop()
            pagView?.removeListener(this)
            pagView = null
        }
        
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
    }

    private fun parseCreationParams() {
        if (creationParams is Map<*, *>) {
            val params = creationParams

            val assetName = params[_argumentAssetName] as? String
            val bytes = params[_argumentBytes] as? ByteArray
            val url = params[_argumentUrl] as? String
            val filePath = params[_argumentFilePath] as? String
            val flutterPackage = params[_argumentPackage] as? String
            val repeatCount = params[_argumentRepeatCount] as? Int ?: 0
            val initProgress = params[_argumentInitProgress] as? Double ?: 0.0
            val autoPlay = params[_argumentAutoPlay] as? Boolean ?: false

            when {
                bytes != null -> {
                    initPagFile(PAGFile.Load(bytes), repeatCount, initProgress, autoPlay)
                }

                assetName != null -> {
                    loadFromAsset(assetName, flutterPackage, repeatCount, initProgress, autoPlay)
                }

                url != null -> {
                    loadFromUrl(url, repeatCount, initProgress, autoPlay)
                }

                filePath != null -> {
                    initPagFile(PAGFile.Load(filePath), repeatCount, initProgress, autoPlay)
                }
            }
        }
    }

    private fun loadFromAsset(
        assetName: String,
        flutterPackage: String?,
        repeatCount: Int,
        initProgress: Double,
        autoPlay: Boolean
    ) {
        try {
            val assetPath = if (flutterPackage.isNullOrEmpty()) {
                "flutter_assets/$assetName"
            } else {
                "flutter_assets/packages/$flutterPackage/$assetName"
            }
            
            val composition = PAGFile.Load(context.assets, assetPath)
            initPagFile(composition, repeatCount, initProgress, autoPlay)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
    
    private fun loadFromUrl(
        url: String,
        repeatCount: Int,
        initProgress: Double,
        autoPlay: Boolean
    ) {
        DataLoadHelper.loadPag(url, { bytes ->
            if (bytes != null) {
                initPagFile(PAGFile.Load(bytes), repeatCount, initProgress, autoPlay)
            }
        }, DataLoadHelper.FROM_PLUGIN)
    }
    
    private fun initPagFile(
        composition: PAGFile?,
        repeatCount: Int,
        initProgress: Double,
        autoPlay: Boolean
    ) {
        if (composition == null) return
        
        this.pagFile = composition
        addPAGViewAndPlay(composition, repeatCount, initProgress, autoPlay)
    }
    
    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            _nativeStart -> {
                pagView?.play()
                result.success(null)
            }
            _nativeStop -> {
                pagView?.stop()
                result.success(null)
            }
            _nativePause -> {
                pagView?.pause()
                result.success(null)
            }
            _nativeSetProgress -> {
                val progress = call.argument<Double>(_argumentProgress) ?: 0.0
                pagView?.progress = progress
                result.success(null)
            }
            _nativeGetPointLayer -> {
                val x = call.argument<Double>(_argumentPointX)?.toFloat() ?: 0f
                val y = call.argument<Double>(_argumentPointY)?.toFloat() ?: 0f
                val layers = pagView?.getLayersUnderPoint(x, y)
                val layerNames = layers?.map { it.layerName() } ?: emptyList()
                result.success(layerNames)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun addPAGViewAndPlay(
        composition: PAGFile,
        repeatCount: Int,
        initProgress: Double,
        autoPlay: Boolean
    ) {
        if (pagView == null) {
            // 直接使用默认构造函数，让 PAGView 使用 Flutter 的 EGL Context
            pagView = PAGView(context)
            pagView?.let { view ->
                view.addListener(this)
                view.layoutParams = RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                view.composition = composition
                view.setRepeatCount(repeatCount)
                view.progress = initProgress
                
                containerView?.addView(view)
                
                // 回传原始尺寸信息给Flutter端
                mainHandler.post {
                    methodChannel?.invokeMethod(
                        "onPAGViewInitialized", mapOf(
                            "width" to composition.width().toDouble(),
                            "height" to composition.height().toDouble(),
                            "viewId" to viewId
                        )
                    )
                }
                
                if (autoPlay) {
                    view.play()
                }
            }
        }
    }

    override fun onAnimationStart(pagView: PAGView?) {
        mainHandler.post {
            methodChannel?.invokeMethod(
                _playCallback, mapOf(
                    "event" to _eventStart,
                    "viewId" to viewId
                )
            )
        }
    }

    override fun onAnimationEnd(pagView: PAGView?) {
        mainHandler.post {
            methodChannel?.invokeMethod(
                _playCallback, mapOf(
                    "event" to _eventEnd,
                    "viewId" to viewId
                )
            )
        }
    }

    override fun onAnimationCancel(pagView: PAGView?) {
        mainHandler.post {
            methodChannel?.invokeMethod(
                _playCallback, mapOf(
                    "event" to _eventCancel,
                    "viewId" to viewId
                )
            )
        }
    }

    override fun onAnimationRepeat(pagView: PAGView?) {
        mainHandler.post {
            methodChannel?.invokeMethod(
                _playCallback, mapOf(
                    "event" to _eventRepeat,
                    "viewId" to viewId
                )
            )
        }
    }

    override fun onAnimationUpdate(pagView: PAGView?) {
        mainHandler.post {
            methodChannel?.invokeMethod(
                _playCallback, mapOf(
                    "event" to _eventUpdate,
                    "viewId" to viewId
                )
            )
        }
    }
}

