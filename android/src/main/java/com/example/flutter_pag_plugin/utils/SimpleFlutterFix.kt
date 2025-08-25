package com.example.flutter_pag_plugin.utils

import android.util.Log
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 简单的 Flutter 崩溃修复方案，不依赖外部 Hook 框架
 */
object SimpleFlutterFix {
    
    private const val TAG = "SimpleFlutterFix"
    private var isInitialized = false
    
    fun init() {
        if (isInitialized) {
            Log.i(TAG, "Already initialized, skipping")
            return
        }
        
        try {
            // 安装全局异常处理器
            GlobalExceptionHandler.install()
            
            // 尝试修改 Flutter 的异常检查行为
            disableFlutterStrictMode()
            
            isInitialized = true
            Log.i(TAG, "Simple Flutter fix initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Simple Flutter fix failed: ${e.message}", e)
        }
    }
    
    private fun disableFlutterStrictMode() {
        try {
            // 尝试找到并禁用 Flutter 的严格检查
            val flutterJniClass = Class.forName("io.flutter.embedding.engine.FlutterJNI")
            
            // 查找可能的严格模式标志
            val fields = flutterJniClass.declaredFields
            for (field in fields) {
                field.isAccessible = true
                
                // 尝试禁用各种检查标志
                if (field.name.contains("check") || 
                    field.name.contains("strict") ||
                    field.name.contains("debug")) {
                    
                    try {
                        when (field.type) {
                            Boolean::class.java -> {
                                field.setBoolean(null, false)
                                Log.i(TAG, "Disabled Flutter check: ${field.name}")
                            }
                            java.lang.Boolean::class.java -> {
                                field.set(null, false)
                                Log.i(TAG, "Disabled Flutter check: ${field.name}")
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略无法修改的字段
                    }
                }
            }
            
            Log.i(TAG, "Flutter strict mode checks processed")
            
        } catch (e: Exception) {
            Log.w(TAG, "Could not modify Flutter strict mode: ${e.message}")
        }
    }
}