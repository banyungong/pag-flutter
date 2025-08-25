package com.example.flutter_pag_plugin.utils

import android.util.Log

/**
 * 全局异常处理器，用于捕获 Flutter Image 相关的崩溃
 */
object GlobalExceptionHandler {
    
    private const val TAG = "GlobalExceptionHandler"
    private var originalHandler: Thread.UncaughtExceptionHandler? = null
    private var isInstalled = false
    
    fun install() {
        if (isInstalled) {
            Log.i(TAG, "Global exception handler already installed")
            return
        }
        
        try {
            originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            
            Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
                if (shouldHandleException(exception)) {
                    Log.w(TAG, "Caught and handled Flutter Image exception: ${exception.message}")
                    // 不崩溃，只记录日志
                    return@setDefaultUncaughtExceptionHandler
                }
                
                // 其他异常交给原始处理器
                originalHandler?.uncaughtException(thread, exception)
            }
            
            isInstalled = true
            Log.i(TAG, "Global exception handler installed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install global exception handler: ${e.message}", e)
        }
    }
    
    fun uninstall() {
        if (!isInstalled) return
        
        try {
            Thread.setDefaultUncaughtExceptionHandler(originalHandler)
            isInstalled = false
            Log.i(TAG, "Global exception handler uninstalled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall global exception handler: ${e.message}", e)
        }
    }
    
    private fun shouldHandleException(exception: Throwable): Boolean {
        val message = exception.message ?: ""
        val stackTrace = exception.stackTraceToString()
        
        // 检查是否是 Flutter Image 相关的异常
        val isImageException = message.contains("Image is already closed") ||
                message.contains("getHardwareBuffer") ||
                message.contains("throwISEIfImageIsInvalid") ||
                message.contains("ImageReader") ||
                message.contains("SurfaceImage")
        
        val isFlutterJniException = stackTrace.contains("ImageGetHardwareBuffer") ||
                stackTrace.contains("platform_view_android_jni_impl") ||
                stackTrace.contains("FlutterJNI") ||
                stackTrace.contains("PlatformViewAndroidJNIImpl") ||
                stackTrace.contains("flutter/fml/platform/android/jni_util.cc") ||
                stackTrace.contains("flutter/shell/platform/android")
        
        val isIllegalStateException = exception is IllegalStateException
        
        // 更宽松的匹配条件
        return (isImageException && isFlutterJniException) || 
               (isIllegalStateException && isFlutterJniException) ||
               (isImageException && isIllegalStateException)
    }
}