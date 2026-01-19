package com.example.flutter_pag_plugin;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.example.flutter_pag_plugin.platfromview.PagFactory;

import org.libpag.FlutterPagPlayerV2;
import org.libpag.PAGFile;
import org.libpag.PAGLayer;
import org.libpag.PAGSurface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.view.TextureRegistry;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/**
 * FlutterPagPlugin
 */
public class FlutterPagPlugin implements FlutterPlugin, MethodCallHandler {
    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private MethodChannel channel;
    TextureRegistry textureRegistry;
    Context context;
    FlutterPlugin.FlutterAssets flutterAssets;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public HashMap<String, IPagPlayer> layerMap = new HashMap<>();
    public HashMap<String, TextureRegistry.SurfaceTextureEntry> entryMap = new HashMap<>();

    // 原生接口
    final static String _nativeInit = "initPag";
    final static String _nativeRelease = "release";
    final static String _nativeStart = "start";
    final static String _nativeStop = "stop";
    final static String _nativePause = "pause";
    final static String _nativeSetProgress = "setProgress";
    final static String _nativeGetPointLayer = "getLayersUnderPoint";

    // 参数
    public final static String _argumentTextureId = "textureId";
    final static String _argumentAssetName = "assetName";
    final static String _argumentPackage = "package";
    final static String _argumentUrl = "url";
    final static String _argumentFilePath = "filePath";
    final static String _argumentBytes = "bytesData";
    final static String _argumentRepeatCount = "repeatCount";
    final static String _argumentInitProgress = "initProgress";
    final static String _argumentAutoPlay = "autoPlay";
    final static String _argumentWidth = "width";
    final static String _argumentHeight = "height";
    final static String _argumentPointX = "x";
    final static String _argumentPointY = "y";
    final static String _argumentProgress = "progress";
    final static String _argumentUsePlayerV2 = "usePlayerV2";
    public final static String _argumentEvent = "PAGEvent";

    // 回调
    public final static String _playCallback = "PAGCallback";
    public final static String _eventStart = "onAnimationStart";
    public final static String _eventEnd = "onAnimationEnd";
    public final static String _eventCancel = "onAnimationCancel";
    public final static String _eventRepeat = "onAnimationRepeat";
    final static String _eventUpdate = "onAnimationUpdate";


    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        flutterAssets = binding.getFlutterAssets();
        channel = new MethodChannel(binding.getBinaryMessenger(), "flutter_pag_plugin");
        channel.setMethodCallHandler(this);
        context = binding.getApplicationContext();
        textureRegistry = binding.getTextureRegistry();
        DataLoadHelper.INSTANCE.initDiskCache(context, DataLoadHelper.INSTANCE.DEFAULT_DIS_SIZE);
        
        // 初始化简单的 Flutter 崩溃修复方案
        try {
            com.example.flutter_pag_plugin.utils.SimpleFlutterFix.INSTANCE.init();
            android.util.Log.i("FlutterPagPlugin", "Simple Flutter fix initialized successfully");
        } catch (Exception e) {
            android.util.Log.e("FlutterPagPlugin", "Simple Flutter fix initialization failed: " + e.getMessage(), e);
        }
        
        // 注册Platform View
        binding.getPlatformViewRegistry().registerViewFactory(
            "flutter_pag_platform_view", 
            new PagFactory(binding.getBinaryMessenger())
        );
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        switch (call.method) {
            case _nativeInit:
                initPag(call, result);
                break;
            case _nativeStart:
                start(call);
                result.success("");
                break;
            case _nativeStop:
                stop(call);
                result.success("");
                break;
            case _nativePause:
                pause(call);
                result.success("");
                break;
            case _nativeSetProgress:
                setProgress(call);
                result.success("");
                break;
            case _nativeRelease:
                release(call);
                result.success("");
                break;
            case _nativeGetPointLayer:
                result.success(getLayersUnderPoint(call));
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void initPag(final MethodCall call, final Result result) {
        String assetName = call.argument(_argumentAssetName);
        byte[] bytes = call.argument(_argumentBytes);
        String url = call.argument(_argumentUrl);
        String filePath = call.argument(_argumentFilePath);
        String flutterPackage = call.argument(_argumentPackage);

        if (bytes != null) {
            initPagPlayerAndCallback(PAGFile.Load(bytes), call, result);
        } else if (assetName != null) {
            String assetKey = "";

            if (flutterAssets != null) {
                if (flutterPackage == null || flutterPackage.isEmpty()) {
                    assetKey = flutterAssets.getAssetFilePathByName(assetName);
                } else {
                    assetKey = flutterAssets.getAssetFilePathByName(assetName, flutterPackage);
                }
            }
            if (assetKey == null) {
                result.error("-1100", "asset资源加载错误", null);
                return;
            }
            PAGFile composition = PAGFile.Load(context.getAssets(), assetKey);
            initPagPlayerAndCallback(composition, call, result);
        } else if (url != null) {
            DataLoadHelper.INSTANCE.loadPag(url, bytes1 -> {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (bytes1 == null) {
                            result.error("-1100", "url资源加载错误", null);
                            return;
                        }
                        initPagPlayerAndCallback(PAGFile.Load(bytes1), call, result);
                    }
                });

                return null;
            }, DataLoadHelper.FROM_PLUGIN);
        } else if (filePath != null && !filePath.isEmpty()) {
            PAGFile composition = PAGFile.Load(filePath);
            initPagPlayerAndCallback(composition, call, result);
        } else {
            result.error("-1100", "未添加资源", null);
        }
    }

    private void initPagPlayerAndCallback(PAGFile composition, MethodCall call, final Result result) {
        if (composition == null) {
            result.error("-1100", "load composition is null! ", null);
            return;
        }

        final int repeatCount = call.argument(_argumentRepeatCount);
        final double initProgress = call.argument(_argumentInitProgress);
        final boolean autoPlay = call.argument(_argumentAutoPlay);
        // 默认使用 FlutterPagPlayerV2，如果参数为 false 则使用 FlutterPagPlayer
        final Boolean usePlayerV2 = call.argument(_argumentUsePlayerV2);
        final boolean useV2 = usePlayerV2 == null || usePlayerV2; // 默认 true

        // 添加日志输出，确认策略是否生效
        android.util.Log.i("FlutterPagPlugin", "initPagPlayerAndCallback: usePlayerV2=" + usePlayerV2 
            + ", useV2=" + useV2 + ", will use " + (useV2 ? "FlutterPagPlayerV2" : "FlutterPagPlayer"));

        final TextureRegistry.SurfaceTextureEntry entry = textureRegistry.createSurfaceTexture();
        entryMap.put(String.valueOf(entry.id()), entry);

        SurfaceTexture surfaceTexture = entry.surfaceTexture();
        surfaceTexture.setDefaultBufferSize(composition.width(), composition.height());

        final Surface surface = new Surface(surfaceTexture);
        final PAGSurface pagSurface = PAGSurface.FromSurface(surface);

        final IPagPlayer pagPlayer;
        
        // 根据策略创建对应的 Player 实例
        if (useV2) {
            // 使用 FlutterPagPlayerV2
            android.util.Log.i("FlutterPagPlugin", "Creating FlutterPagPlayerV2 instance");
            FlutterPagPlayerV2 playerV2 = new FlutterPagPlayerV2();
            playerV2.init(context, composition, repeatCount, initProgress, channel, entry.id());
            playerV2.setSurface(pagSurface);
            pagPlayer = playerV2;
            android.util.Log.i("FlutterPagPlugin", "FlutterPagPlayerV2 created successfully, textureId=" + entry.id());
        } else {
            // 使用 FlutterPagPlayer
            android.util.Log.i("FlutterPagPlugin", "Creating FlutterPagPlayer instance");
            FlutterPagPlayer playerV1 = new FlutterPagPlayer();
            playerV1.init(composition, repeatCount, initProgress, channel, entry.id());
            playerV1.setSurface(pagSurface);
            pagPlayer = playerV1;
            android.util.Log.i("FlutterPagPlugin", "FlutterPagPlayer created successfully, textureId=" + entry.id());
        }

        // 设置释放监听器
        pagPlayer.setReleaseListener(() -> {
            // 按照正确的依赖顺序释放：先释放依赖其他资源的对象
            // 1. 先释放 PAGSurface（它依赖 Surface）
            try {
                if (pagSurface != null) {
                    pagSurface.release();
                }
            } catch (Exception e) {
                android.util.Log.e("FlutterPagPlugin", "Error releasing PAGSurface: " + e.getMessage(), e);
            }
            // 2. 然后释放 Surface（它依赖 SurfaceTexture）
            try {
                surface.release();
            } catch (Exception e) {
                android.util.Log.e("FlutterPagPlugin", "Error releasing Surface: " + e.getMessage(), e);
            }
            // 3. 最后释放 SurfaceTextureEntry
            try {
                entry.release();
            } catch (Exception e) {
                android.util.Log.e("FlutterPagPlugin", "Error releasing SurfaceTextureEntry: " + e.getMessage(), e);
            }
        });

        layerMap.put(String.valueOf(entry.id()), pagPlayer);
        final HashMap<String, Object> callback = new HashMap<>();
        callback.put(_argumentTextureId, entry.id());
        callback.put(_argumentWidth, (double) composition.width());
        callback.put(_argumentHeight, (double) composition.height());
        handler.post(() -> {
            pagPlayer.flush();
            if (autoPlay) {
                pagPlayer.start();
            }
            result.success(callback);
        });
    }

    void start(MethodCall call) {
        IPagPlayer flutterPagPlayer = getFlutterPagPlayer(call);
        if (flutterPagPlayer != null) {
            flutterPagPlayer.start();
        }
    }

    void stop(MethodCall call) {
        IPagPlayer flutterPagPlayer = getFlutterPagPlayer(call);
        if (flutterPagPlayer != null) {
            flutterPagPlayer.stop();
        }
    }

    void pause(MethodCall call) {
        IPagPlayer flutterPagPlayer = getFlutterPagPlayer(call);
        if (flutterPagPlayer != null) {
            flutterPagPlayer.pause();
        }
    }

    void setProgress(MethodCall call) {
        double progress = call.argument(_argumentProgress);
        IPagPlayer flutterPagPlayer = getFlutterPagPlayer(call);
        if (flutterPagPlayer != null) {
            flutterPagPlayer.setProgressValue(progress);
        }
    }

    void release(MethodCall call) {
        String textureId = getTextureId(call);
        IPagPlayer flutterPagPlayer = layerMap.remove(textureId);
        
        // 从 entryMap 中移除，但不在这里释放
        // entry 的释放由 releaseListener 统一处理，避免重复释放导致的崩溃
        entryMap.remove(textureId);
        
        if (flutterPagPlayer != null) {
            // 在主线程上执行释放，确保线程安全
            handler.post(() -> {
                try {
                    flutterPagPlayer.stop();
                    flutterPagPlayer.release();
                    // releaseListener 会在 release() 方法中自动释放 entry, surface, pagSurface
                } catch (Exception e) {
                    android.util.Log.e("FlutterPagPlugin", "Error releasing FlutterPagPlayer: " + e.getMessage(), e);
                }
            });
        }
    }

    List<String> getLayersUnderPoint(MethodCall call) {
        IPagPlayer flutterPagPlayer = getFlutterPagPlayer(call);

        List<String> layerNames = new ArrayList<>();
        PAGLayer[] layers = null;
        if (flutterPagPlayer != null) {
            layers = flutterPagPlayer.getLayersUnderPoint(
                    ((Double) call.argument(_argumentPointX)).floatValue(), ((Double) call.argument(_argumentPointY)).floatValue());
        }

        if (layers != null) {
            for (PAGLayer layer : layers) {
                layerNames.add(layer.layerName());
            }
        }

        return layerNames;
    }

    IPagPlayer getFlutterPagPlayer(MethodCall call) {
        return layerMap.get(getTextureId(call));
    }

    String getTextureId(MethodCall call) {
        return "" + call.argument(_argumentTextureId);
    }

    //插件销毁
    public void onDestroy() {
        // 先释放所有的 pagPlayer，releaseListener 会自动释放 entry, surface, pagSurface
        for (IPagPlayer pagPlayer : layerMap.values()) {
            try {
                pagPlayer.stop();
                pagPlayer.release();
                // releaseListener 会在 release() 方法中自动释放 entry, surface, pagSurface
            } catch (Exception e) {
                android.util.Log.e("FlutterPagPlugin", "Error releasing pagPlayer in onDestroy: " + e.getMessage(), e);
            }
        }
        // 清理剩余的 entry（可能有些 entry 没有对应的 pagPlayer，虽然理论上不应该发生）
        for (TextureRegistry.SurfaceTextureEntry entry : entryMap.values()) {
            try {
                entry.release();
            } catch (Exception e) {
                android.util.Log.e("FlutterPagPlugin", "Error releasing entry in onDestroy: " + e.getMessage(), e);
            }
        }
        layerMap.clear();
        entryMap.clear();
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
        onDestroy();
    }
}
