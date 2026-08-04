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
    @Volatile
    private var disposed = false

    // 常量定义（与主插件保持一致）
    companion object {
        const val _nativeStart = "start"
        const val _nativeStop = "stop"
        const val _nativePause = "pause"
        const val _nativeSetProgress = "setProgress"
        const val _nativeGetPointLayer = "getLayersUnderPoint"
        
        // 新增：切换PAG文件的方法
        const val _nativeLoadPagFile = "loadPagFile"
        // 新增：隐藏/显示PAGView的方法
        const val _nativeSetVisibility = "setVisibility"
        // 新增：清空画面的方法（重置到第一帧或清空）
        const val _nativeClearFrame = "clearFrame"

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
        // 新增：可见性参数
        const val _argumentVisible = "visible"

        const val _playCallback = "PAGCallback"
        const val _eventStart = "onAnimationStart"
        const val _eventEnd = "onAnimationEnd"
        const val _eventCancel = "onAnimationCancel"
        const val _eventRepeat = "onAnimationRepeat"
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
        Log.d("PlatformPagView", "getView: viewId=$viewId")
        return containerView
    }

    override fun dispose() {
        synchronized(this) {
            if (disposed) {
                return
            }
            disposed = true
        }
        Log.d("PlatformPagView", "dispose: viewId=$viewId")

        if (Looper.myLooper() == Looper.getMainLooper()) {
            releaseNativeView()
        } else {
            mainHandler.postAtFrontOfQueue {
                releaseNativeView()
            }
        }
    }

    private fun releaseNativeView() {
        mainHandler.removeCallbacksAndMessages(null)

        val view = pagView
        view?.removeListener(this)
        view?.pause()
        view?.stop()
        if (view != null) {
            containerView?.removeView(view)
        }

        pagFile = null
        pagView = null
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
        containerView = null
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

            // 先创建PAGView，然后加载PAG文件
            createPAGViewIfNeeded()
            
            when {
                bytes != null -> {
                    loadPagFile(PAGFile.Load(bytes), repeatCount, initProgress, autoPlay)
                }

                assetName != null -> {
                    loadFromAsset(assetName, flutterPackage, repeatCount, initProgress, autoPlay)
                }

                url != null -> {
                    loadFromUrl(url, repeatCount, initProgress, autoPlay)
                }

                filePath != null -> {
                    loadPagFile(PAGFile.Load(filePath), repeatCount, initProgress, autoPlay)
                }
            }
        }
    }

    /**
     * 创建PAGView（如果还没有创建的话）
     */
    private fun createPAGViewIfNeeded() {
        if (disposed) {
            return
        }
        if (pagView == null) {
            pagView = PAGView(context)
            pagView?.let { view ->
                view.addListener(this)
                view.layoutParams = RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                containerView?.addView(view)
                Log.d("PlatformPagView", "PAGView created for viewId=$viewId")
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
        if (disposed) {
            return
        }
        try {
            val assetPath = if (flutterPackage.isNullOrEmpty()) {
                "flutter_assets/$assetName"
            } else {
                "flutter_assets/packages/$flutterPackage/$assetName"
            }
            
            val composition = PAGFile.Load(context.assets, assetPath)
            loadPagFile(composition, repeatCount, initProgress, autoPlay)
        } catch (e: IOException) {
            Log.e("PlatformPagView", "Failed to load asset: $assetName", e)
        }
    }
    
    private fun loadFromUrl(
        url: String,
        repeatCount: Int,
        initProgress: Double,
        autoPlay: Boolean
    ) {
        DataLoadHelper.loadPag(url, { bytes ->
            if (disposed) {
                return@loadPag
            }

            val composition = bytes?.let { PAGFile.Load(it) }
            mainHandler.post {
                if (disposed) {
                    return@post
                }
                if (composition != null) {
                    loadPagFile(composition, repeatCount, initProgress, autoPlay)
                } else {
                    Log.e("PlatformPagView", "Failed to load PAG from URL: $url")
                }
            }
        }, DataLoadHelper.FROM_PLUGIN)
    }
    
    /**
     * 加载新的PAG文件到现有的PAGView中
     */
    private fun loadPagFile(
        composition: PAGFile?,
        repeatCount: Int,
        initProgress: Double,
        autoPlay: Boolean
    ) {
        if (disposed) {
            return
        }
        if (composition == null) {
            Log.e("PlatformPagView", "PAG composition is null")
            return
        }
        
        // 确保PAGView已创建
        createPAGViewIfNeeded()
        
        // 停止当前播放
        pagView?.stop()
        
        // 设置新的composition
        this.pagFile = composition
        pagView?.let { view ->
            view.composition = composition
            view.setRepeatCount(repeatCount)
            view.progress = initProgress
            
            // 显示PAGView（加载新文件时默认显示）
            view.visibility = View.VISIBLE
            
            // 回传尺寸信息给Flutter端
            mainHandler.post {
                if (disposed) {
                    return@post
                }
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
            
            Log.d("PlatformPagView", "PAG file loaded successfully for viewId=$viewId, size=${composition.width()}x${composition.height()}")
        }
    }
    
    private fun handleMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (disposed) {
            result.success(null)
            return
        }
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
            // 新增：动态加载PAG文件
            _nativeLoadPagFile -> {
                loadPagFileFromMethodCall(call, result)
            }
            // 新增：设置可见性
            _nativeSetVisibility -> {
                val visible = call.argument<Boolean>(_argumentVisible) ?: true
                setViewVisibility(visible)
                result.success(null)
            }
            // 新增：清空画面
            _nativeClearFrame -> {
                clearFrame()
                result.success(null)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    /**
     * 处理动态加载PAG文件的方法调用
     */
    private fun loadPagFileFromMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (disposed) {
            result.success(null)
            return
        }
        Log.d("PlatformPagView", "loadPagFileFromMethodCall: viewId=$viewId")

        val assetName = call.argument<String>(_argumentAssetName)
        val bytes = call.argument<ByteArray>(_argumentBytes)
        val url = call.argument<String>(_argumentUrl)
        val filePath = call.argument<String>(_argumentFilePath)
        val flutterPackage = call.argument<String>(_argumentPackage)
        val repeatCount = call.argument<Int>(_argumentRepeatCount) ?: 0
        val initProgress = call.argument<Double>(_argumentInitProgress) ?: 0.0
        val autoPlay = call.argument<Boolean>(_argumentAutoPlay) ?: false

        when {
            bytes != null -> {
                loadPagFile(PAGFile.Load(bytes), repeatCount, initProgress, autoPlay)
                result.success(null)
            }

            assetName != null -> {
                loadFromAsset(assetName, flutterPackage, repeatCount, initProgress, autoPlay)
                result.success(null)
            }

            url != null -> {
                loadFromUrl(url, repeatCount, initProgress, autoPlay)
                result.success(null)
            }

            filePath != null -> {
                loadPagFile(PAGFile.Load(filePath), repeatCount, initProgress, autoPlay)
                result.success(null)
            }

            else -> {
                result.error("INVALID_ARGUMENTS", "No valid PAG source provided", null)
            }
        }
    }

    /**
     * 设置PAGView的可见性
     */
    private fun setViewVisibility(visible: Boolean) {
        if (disposed) {
            return
        }
        mainHandler.post {
            if (disposed) {
                return@post
            }
            pagView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
//            Log.d("PlatformPagView", "PAGView visibility set to: ${if (visible) "VISIBLE" : "INVISIBLE"} for viewId=$viewId")
        }
    }

    /**
     * 清空画面 - 停止播放并重置到第一帧或隐藏
     */
    private fun clearFrame() {
        if (disposed) {
            return
        }
        mainHandler.post {
            if (disposed) {
                return@post
            }
            pagView?.let { view ->
                view.stop()
                // 重置到第一帧
                view.progress = 0.0
                // 强制刷新画面
                view.flush()
                Log.d("PlatformPagView", "Frame cleared for viewId=$viewId")
            }
        }
    }

    private fun sendAnimationEvent(event: String) {
        if (disposed) {
            return
        }
        mainHandler.post {
            if (disposed) {
                return@post
            }
            methodChannel?.invokeMethod(
                _playCallback, mapOf(
                    "event" to event,
                    "viewId" to viewId
                )
            )
        }
    }

    override fun onAnimationStart(pagView: PAGView?) {
        sendAnimationEvent(_eventStart)
    }

    override fun onAnimationEnd(pagView: PAGView?) {
        sendAnimationEvent(_eventEnd)
    }

    override fun onAnimationCancel(pagView: PAGView?) {
        sendAnimationEvent(_eventCancel)
    }

    override fun onAnimationRepeat(pagView: PAGView?) {
        sendAnimationEvent(_eventRepeat)
    }

    override fun onAnimationUpdate(pagView: PAGView?) = Unit
}
