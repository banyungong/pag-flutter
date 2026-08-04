import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

typedef PAGCallback = void Function();

/// PAG渲染模式
enum PAGRenderMode {
  /// 使用Texture渲染（默认，性能更好）
  texture,

  /// 使用PlatformView渲染（更好的原生集成）
  platformView,
}

class PAGView extends StatefulWidget {
  final double? width;
  final double? height;

  /// 二进制动画数据
  final Uint8List? bytesData;

  /// 网络资源，动画链接
  final String? url;

  /// flutter动画资源路径
  final String? assetName;

  /// 文件资源
  final String? filePath;

  /// asset package
  final String? package;

  /// 初始化时播放进度
  final double initProgress;

  /// 初始化后自动播放
  final bool autoPlay;

  /// 循环次数
  final int repeatCount;

  /// 是否使用 FlutterPagPlayerV2（Android 专用）
  /// true 或 null：使用 FlutterPagPlayerV2（默认）
  /// false：使用 FlutterPagPlayer
  final bool? usePlayerV2;

  /// 渲染模式 iOS下PlatformView模式不可用
  final PAGRenderMode renderMode;

  /// 初始化完成
  final PAGCallback? onInit;

  /// Notifies the start of the animation.
  final PAGCallback? onAnimationStart;

  /// Notifies the end of the animation.
  final PAGCallback? onAnimationEnd;

  /// Notifies the cancellation of the animation.
  final PAGCallback? onAnimationCancel;

  /// Notifies the repetition of the animation.
  final PAGCallback? onAnimationRepeat;

  /// 加载失败时的默认控件构造器
  final Widget Function(BuildContext context)? defaultBuilder;

  static const int REPEAT_COUNT_LOOP = -1; //无限循环
  static const int REPEAT_COUNT_DEFAULT = 1; //默认仅播放一次

  PAGView.network(
    this.url, {
    this.width,
    this.height,
    this.repeatCount = REPEAT_COUNT_DEFAULT,
    this.initProgress = 0,
    this.autoPlay = false,
    this.renderMode = PAGRenderMode.texture,
    this.usePlayerV2,
    this.onInit,
    this.onAnimationStart,
    this.onAnimationEnd,
    this.onAnimationCancel,
    this.onAnimationRepeat,
    this.defaultBuilder,
    Key? key,
    this.bytesData = null,
    this.assetName = null,
    this.filePath = null,
    this.package = null,
  }) : super(key: key);

  PAGView.asset(
    this.assetName, {
    this.width,
    this.height,
    this.repeatCount = REPEAT_COUNT_DEFAULT,
    this.initProgress = 0,
    this.autoPlay = false,
    this.renderMode = PAGRenderMode.texture,
    this.usePlayerV2,
    this.package,
    this.onInit,
    this.onAnimationStart,
    this.onAnimationEnd,
    this.onAnimationCancel,
    this.onAnimationRepeat,
    this.defaultBuilder,
    Key? key,
    this.bytesData,
    this.url,
    this.filePath,
  }) : super(key: key);

  @Deprecated('')
  PAGView.bytes(
    this.bytesData, {
    this.width,
    this.height,
    this.repeatCount = REPEAT_COUNT_DEFAULT,
    this.initProgress = 0,
    this.autoPlay = false,
    this.renderMode = PAGRenderMode.texture,
    this.usePlayerV2,
    this.package,
    this.onInit,
    this.onAnimationStart,
    this.onAnimationEnd,
    this.onAnimationCancel,
    this.onAnimationRepeat,
    this.defaultBuilder,
    Key? key,
    this.url,
    this.assetName,
    this.filePath,
  }) : super(key: key);

  PAGView.filePath(
    this.filePath, {
    this.width,
    this.height,
    this.repeatCount = REPEAT_COUNT_DEFAULT,
    this.initProgress = 0,
    this.autoPlay = false,
    this.renderMode = PAGRenderMode.texture,
    this.usePlayerV2,
    this.package,
    this.onInit,
    this.onAnimationStart,
    this.onAnimationEnd,
    this.onAnimationCancel,
    this.onAnimationRepeat,
    this.defaultBuilder,
    Key? key,
    this.url,
    this.assetName,
    this.bytesData,
  }) : super(key: key);

  @override
  PAGViewState createState() => PAGViewState();
}

class PAGViewState extends State<PAGView> {
  bool _hasLoadTexture = false;
  bool _released = false;
  int _textureId = -1;

  // PlatformView相关
  MethodChannel? _platformViewChannel;

  double rawWidth = 0;
  double rawHeight = 0;

  // PlatformView模式下的原始尺寸
  double platformViewRawWidth = 0;
  double platformViewRawHeight = 0;

  // 原生接口
  static const String _nativeInit = 'initPag';
  static const String _nativeRelease = 'release';
  static const String _nativeStart = 'start';
  static const String _nativeStop = 'stop';
  static const String _nativePause = 'pause';
  static const String _nativeSetProgress = 'setProgress';
  static const String _nativeGetPointLayer = 'getLayersUnderPoint';

  // 新增：PlatformView专用接口
  static const String _nativeLoadPagFile = 'loadPagFile';
  static const String _nativeSetVisibility = 'setVisibility';
  static const String _nativeClearFrame = 'clearFrame';

  // 参数
  static const String _argumentTextureId = 'textureId';
  static const String _argumentAssetName = 'assetName';
  static const String _argumentPackage = 'package';
  static const String _argumentUrl = 'url';
  static const String _argumentBytes = 'bytesData';
  static const String _argumentFilePath = "filePath";
  static const String _argumentRepeatCount = 'repeatCount';
  static const String _argumentInitProgress = 'initProgress';
  static const String _argumentAutoPlay = 'autoPlay';
  static const String _argumentWidth = 'width';
  static const String _argumentHeight = 'height';
  static const String _argumentPointX = 'x';
  static const String _argumentPointY = 'y';
  static const String _argumentProgress = 'progress';
  static const String _argumentUsePlayerV2 = 'usePlayerV2';
  static const String _argumentEvent = 'PAGEvent';

  // 新增：可见性参数
  static const String _argumentVisible = 'visible';

  // 监听该函数
  static const String _playCallback = 'PAGCallback';
  static const String _eventStart = 'onAnimationStart';
  static const String _eventEnd = 'onAnimationEnd';
  static const String _eventCancel = 'onAnimationCancel';
  static const String _eventRepeat = 'onAnimationRepeat';

  // 回调监听
  static MethodChannel _channel = (const MethodChannel('flutter_pag_plugin')
    ..setMethodCallHandler((result) {
      if (result.method == _playCallback) {
        callbackHandlers[result.arguments[_argumentTextureId]]?.call(
          result.arguments[_argumentEvent],
        );
      }

      return Future<dynamic>.value();
    }));

  static Map<int, Function(String event)?> callbackHandlers = {};

  @override
  void initState() {
    super.initState();
    if (widget.renderMode == PAGRenderMode.texture || Platform.isIOS) {
      newTexture();
    } else {
      setState(() {
        _hasLoadTexture = true;
      });
      widget.onInit?.call();
    }
  }

  // 初始化 Texture 模式
  void newTexture() async {
    int repeatCount = widget.repeatCount <= 0 &&
            widget.repeatCount != PAGView.REPEAT_COUNT_LOOP
        ? PAGView.REPEAT_COUNT_DEFAULT
        : widget.repeatCount;
    double initProcess = widget.initProgress < 0 ? 0 : widget.initProgress;

    try {
      dynamic result = await _channel.invokeMethod(_nativeInit, {
        _argumentAssetName: widget.assetName,
        _argumentPackage: widget.package,
        _argumentUrl: widget.url,
        _argumentFilePath: widget.filePath,
        _argumentBytes: widget.bytesData,
        _argumentRepeatCount: repeatCount,
        _argumentInitProgress: initProcess,
        _argumentAutoPlay: widget.autoPlay,
        if (widget.usePlayerV2 != null)
          _argumentUsePlayerV2: widget.usePlayerV2,
      });
      if (result is Map) {
        _textureId = result[_argumentTextureId];
        rawWidth = result[_argumentWidth] ?? 0;
        rawHeight = result[_argumentHeight] ?? 0;
      }
      if (!mounted || _released) {
        if (_textureId >= 0) {
          await _channel.invokeMethod(_nativeRelease, {
            _argumentTextureId: _textureId,
          });
        }
        return;
      }

      _hasLoadTexture = true;
      setState(() {});
      widget.onInit?.call();
    } catch (e) {
      print('PAGViewState error: $e');
    }

    // 事件回调
    if (_textureId >= 0) {
      var events = <String, PAGCallback?>{
        _eventStart: widget.onAnimationStart,
        _eventEnd: widget.onAnimationEnd,
        _eventCancel: widget.onAnimationCancel,
        _eventRepeat: widget.onAnimationRepeat,
      };
      callbackHandlers[_textureId] = (event) {
        events[event]?.call();
      };
    }
  }

  // 设置PlatformView的方法通道
  void _onPlatformViewCreated(int viewId) {
    if (!mounted || _released) {
      return;
    }
    _platformViewChannel = MethodChannel('flutter_pag_platform_view_$viewId');
    _platformViewChannel?.setMethodCallHandler(_handlePlatformViewCallback);
  }

  // 处理PlatformView回调
  Future<void> _handlePlatformViewCallback(MethodCall call) async {
    if (!mounted || _released) {
      return;
    }
    if (call.method == _playCallback) {
      final event = call.arguments['event'] as String?;
      if (event != null) {
        switch (event) {
          case _eventStart:
            widget.onAnimationStart?.call();
            break;
          case _eventEnd:
            widget.onAnimationEnd?.call();
            break;
          case _eventCancel:
            widget.onAnimationCancel?.call();
            break;
          case _eventRepeat:
            widget.onAnimationRepeat?.call();
            break;
        }
      }
    } else if (call.method == 'onPAGViewInitialized') {
      // 处理尺寸信息回调
      final width = call.arguments['width'] as double?;
      final height = call.arguments['height'] as double?;
      print("onPAGViewInitialized:${width},${height}");
      if (mounted && !_released && width != null && height != null) {
        setState(() {
          platformViewRawWidth = width;
          platformViewRawHeight = height;
        });
      }
    }
  }

  /// 开始
  void start() {
    if (!_hasLoadTexture) {
      return;
    }
    if (widget.renderMode == PAGRenderMode.texture || Platform.isIOS) {
      _channel.invokeMethod(_nativeStart, {_argumentTextureId: _textureId});
    } else {
      _platformViewChannel?.invokeMethod(_nativeStart);
    }
  }

  /// 停止
  void stop() {
    if (!_hasLoadTexture) {
      return;
    }
    if (widget.renderMode == PAGRenderMode.texture || Platform.isIOS) {
      _channel.invokeMethod(_nativeStop, {_argumentTextureId: _textureId});
    } else {
      _platformViewChannel?.invokeMethod(_nativeStop);
    }
  }

  /// 暂停
  void pause() {
    if (!_hasLoadTexture) {
      return;
    }
    if (widget.renderMode == PAGRenderMode.texture || Platform.isIOS) {
      _channel.invokeMethod(_nativePause, {_argumentTextureId: _textureId});
    } else {
      _platformViewChannel?.invokeMethod(_nativePause);
    }
  }

  /// 设置进度
  void setProgress(double progress) {
    if (!_hasLoadTexture) {
      return;
    }
    if (widget.renderMode == PAGRenderMode.texture || Platform.isIOS) {
      _channel.invokeMethod(_nativeSetProgress, {
        _argumentTextureId: _textureId,
        _argumentProgress: progress,
      });
    } else {
      _platformViewChannel?.invokeMethod(_nativeSetProgress, {
        _argumentProgress: progress,
      });
    }
  }

  /// 获取某一位置的图层
  Future<List<String>> getLayersUnderPoint(double x, double y) async {
    if (!_hasLoadTexture) {
      return [];
    }
    if (widget.renderMode == PAGRenderMode.texture || Platform.isIOS) {
      return (await _channel.invokeMethod(_nativeGetPointLayer, {
        _argumentTextureId: _textureId,
        _argumentPointX: x,
        _argumentPointY: y,
      }) as List)
          .map((e) => e.toString())
          .toList();
    } else {
      final result = await _platformViewChannel?.invokeMethod(
        _nativeGetPointLayer,
        {_argumentPointX: x, _argumentPointY: y},
      );
      return (result as List?)?.map((e) => e.toString()).toList() ?? [];
    }
  }

  /// 动态加载新的PAG文件（仅PlatformView模式支持）
  Future<void> loadPagFile({
    String? assetName,
    String? package,
    String? url,
    String? filePath,
    Uint8List? bytes,
    int repeatCount = PAGView.REPEAT_COUNT_DEFAULT,
    double initProgress = 0.0,
    bool autoPlay = true,
  }) async {
    if (widget.renderMode != PAGRenderMode.platformView || Platform.isIOS) {
      throw UnsupportedError('loadPagFile 只支持 PlatformView 模式');
    }

    if (!_hasLoadTexture) {
      return;
    }

    await _platformViewChannel?.invokeMethod(_nativeLoadPagFile, {
      if (assetName != null) _argumentAssetName: assetName,
      if (package != null) _argumentPackage: package,
      if (url != null) _argumentUrl: url,
      if (filePath != null) _argumentFilePath: filePath,
      if (bytes != null) _argumentBytes: bytes,
      _argumentRepeatCount: repeatCount,
      _argumentInitProgress: initProgress,
      _argumentAutoPlay: autoPlay,
    });
  }

  /// 设置可见性（仅PlatformView模式支持）
  Future<void> setVisibility(bool visible) async {
    if (widget.renderMode != PAGRenderMode.platformView || Platform.isIOS) {
      throw UnsupportedError('setVisibility 只支持 PlatformView 模式');
    }

    if (!_hasLoadTexture) {
      return;
    }

    await _platformViewChannel?.invokeMethod(_nativeSetVisibility, {
      _argumentVisible: visible,
    });
  }

  /// 隐藏动画视图（仅PlatformView模式支持）
  Future<void> hide() async => setVisibility(false);

  /// 显示动画视图（仅PlatformView模式支持）
  Future<void> show() async => setVisibility(true);

  /// 清空画面并重置到第一帧（仅PlatformView模式支持）
  Future<void> clearFrame() async {
    if (widget.renderMode != PAGRenderMode.platformView || Platform.isIOS) {
      throw UnsupportedError('clearFrame 只支持 PlatformView 模式');
    }

    if (!_hasLoadTexture) {
      return;
    }

    await _platformViewChannel?.invokeMethod(_nativeClearFrame);
  }

  /// 停止播放并隐藏（仅PlatformView模式支持）
  Future<void> stopAndHide() async {
    stop();
    await hide();
  }

  /// 清空画面并隐藏（仅PlatformView模式支持）
  Future<void> clearAndHide() async {
    await clearFrame();
    await hide();
  }

  ///释放资源
  Future<void> release() async {
    if (_released) {
      return;
    }
    _released = true;
    _hasLoadTexture = false;

    if (widget.renderMode == PAGRenderMode.texture || Platform.isIOS) {
      callbackHandlers.remove(_textureId);
      if (_textureId >= 0) {
        await _channel.invokeMethod(_nativeRelease, {
          _argumentTextureId: _textureId,
        });
      }
    } else {
      _platformViewChannel?.setMethodCallHandler(null);
      _platformViewChannel = null;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_hasLoadTexture) {
      if (widget.renderMode == PAGRenderMode.texture || Platform.isIOS) {
        return Container(
          width: widget.width ?? (rawWidth / 2),
          height: widget.height ?? (rawHeight / 2),
          child: Texture(textureId: _textureId),
        );
      } else {
        // PlatformView模式
        return Container(
          width: widget.width ??
              (platformViewRawWidth > 0 ? platformViewRawWidth / 2 : 100),
          height: widget.height ??
              (platformViewRawHeight > 0 ? platformViewRawHeight / 2 : 100),
          child: _buildPlatformView(),
        );
      }
    } else {
      return widget.defaultBuilder?.call(context) ?? Container();
    }
  }

  Widget _buildPlatformView() {
    // 创建参数
    final creationParams = <String, dynamic>{
      _argumentAssetName: widget.assetName,
      _argumentPackage: widget.package,
      _argumentUrl: widget.url,
      _argumentFilePath: widget.filePath,
      _argumentBytes: widget.bytesData,
      _argumentRepeatCount: widget.repeatCount <= 0 &&
              widget.repeatCount != PAGView.REPEAT_COUNT_LOOP
          ? PAGView.REPEAT_COUNT_DEFAULT
          : widget.repeatCount,
      _argumentInitProgress: widget.initProgress < 0 ? 0 : widget.initProgress,
      _argumentAutoPlay: widget.autoPlay,
    };

    // 根据平台返回相应的PlatformView
    if (Theme.of(context).platform == TargetPlatform.android) {
      return AndroidView(
        viewType: 'flutter_pag_platform_view',
        onPlatformViewCreated: _onPlatformViewCreated,
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
      );
    } else if (Theme.of(context).platform == TargetPlatform.iOS) {
      return UiKitView(
        viewType: 'flutter_pag_platform_view',
        onPlatformViewCreated: _onPlatformViewCreated,
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
      );
    }

    // 其他平台暂不支持PlatformView，回退到默认Builder
    return widget.defaultBuilder?.call(context) ??
        Container(child: Center(child: Text('该平台暂不支持PlatformView模式')));
  }

  @override
  void dispose() {
    release();
    super.dispose();
  }
}
