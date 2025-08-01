package com.example.flutter_pag_plugin.platfromview

import android.content.Context
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

class PagFactory(private val binaryMessenger: BinaryMessenger): PlatformViewFactory(
    StandardMessageCodec.INSTANCE
) {
    override fun create(
        context: Context?,
        viewId: Int,
        args: Any?
    ): PlatformView {
        return PlatformPagView(context!!, binaryMessenger, viewId, args)
    }
}