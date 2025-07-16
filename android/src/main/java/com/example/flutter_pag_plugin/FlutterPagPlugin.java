package com.example.flutter_pag_plugin;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import org.libpag.PAGFile;
import org.libpag.PAGLayer;
import org.libpag.PAGSurface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.view.FlutterNativeView;
import io.flutter.view.TextureRegistry;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

public class FlutterPagPlugin implements FlutterPlugin, MethodCallHandler {
    private static final String TAG = "FlutterPagPlugin";

    private MethodChannel channel;
    TextureRegistry textureRegistry;
    Context context;
    io.flutter.plugin.common.PluginRegistry.Registrar registrar;
    FlutterPlugin.FlutterAssets flutterAssets;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 后台处理线程
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private ExecutorService fileLoadExecutor;

    public HashMap<String, FlutterPagPlayer> layerMap = new HashMap<String, FlutterPagPlayer>();
    public HashMap<String, TextureRegistry.SurfaceTextureEntry> entryMap = new HashMap<String, TextureRegistry.SurfaceTextureEntry>();

    // 原生接口
    final static String _nativeInit = "initPag";
    final static String _nativeRelease = "release";
    final static String _nativeStart = "start";
    final static String _nativeStop = "stop";
    final static String _nativePause = "pause";
    final static String _nativeSetProgress = "setProgress";
    final static String _nativeGetPointLayer = "getLayersUnderPoint";

    // 参数
    final static String _argumentTextureId = "textureId";
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
    final static String _argumentEvent = "PAGEvent";

    // 回调
    final static String _playCallback = "PAGCallback";
    final static String _eventStart = "onAnimationStart";
    final static String _eventEnd = "onAnimationEnd";
    final static String _eventCancel = "onAnimationCancel";
    final static String _eventRepeat = "onAnimationRepeat";
    final static String _eventUpdate = "onAnimationUpdate";

    public FlutterPagPlugin() {
        initBackgroundThreads();
    }

    public FlutterPagPlugin(io.flutter.plugin.common.PluginRegistry.Registrar registrar) {
        this.registrar = registrar;
        textureRegistry = registrar.textures();
        context = registrar.context();
        initBackgroundThreads();
        DataLoadHelper.INSTANCE.initDiskCache(context, DataLoadHelper.INSTANCE.DEFAULT_DIS_SIZE);
    }

    private void initBackgroundThreads() {
        // 创建后台线程用于PAG操作
        backgroundThread = new HandlerThread("PAGBackgroundThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        // 文件加载线程池
        fileLoadExecutor = Executors.newCachedThreadPool();
    }

    public static void registerWith(io.flutter.plugin.common.PluginRegistry.Registrar registrar) {
        final FlutterPagPlugin plugin = new FlutterPagPlugin(registrar);
        registrar.addViewDestroyListener(new PluginRegistry.ViewDestroyListener() {
            @Override
            public boolean onViewDestroy(FlutterNativeView flutterNativeView) {
                plugin.onDestroy();
                return false; // We are not interested in assuming ownership of the NativeView.
            }
        });
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        flutterAssets = binding.getFlutterAssets();
        channel = new MethodChannel(binding.getBinaryMessenger(), "flutter_pag_plugin");
        channel.setMethodCallHandler(this);
        context = binding.getApplicationContext();
        textureRegistry = binding.getTextureRegistry();
        DataLoadHelper.INSTANCE.initDiskCache(context, DataLoadHelper.INSTANCE.DEFAULT_DIS_SIZE);
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
            // 在后台线程加载PAG文件
            fileLoadExecutor.execute(() -> {
                try {
                    PAGFile composition = loadPAGFileSafely(bytes);
                    mainHandler.post(() -> initPagPlayerAndCallback(composition, call, result));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load PAG from bytes", e);
                    mainHandler.post(() -> result.error("-1100", "PAG文件加载失败: " + e.getMessage(), null));
                }
            });
        } else if (assetName != null) {
            fileLoadExecutor.execute(() -> {
                try {
                    String assetKey = getAssetKey(assetName, flutterPackage);
                    if (assetKey == null) {
                        mainHandler.post(() -> result.error("-1100", "asset资源加载错误", null));
                        return;
                    }
                    PAGFile composition = loadPAGFileFromAssetSafely(assetKey);
                    mainHandler.post(() -> initPagPlayerAndCallback(composition, call, result));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load PAG from asset", e);
                    mainHandler.post(() -> result.error("-1100", "Asset加载失败: " + e.getMessage(), null));
                }
            });
        } else if (url != null) {
            DataLoadHelper.INSTANCE.loadPag(url, new Function1<byte[], Unit>() {
                @Override
                public Unit invoke(final byte[] bytes) {
                    if (bytes == null) {
                        mainHandler.post(() -> result.error("-1100", "url资源加载错误", null));
                        return null;
                    }

                    fileLoadExecutor.execute(() -> {
                        try {
                            PAGFile composition = loadPAGFileSafely(bytes);
                            mainHandler.post(() -> initPagPlayerAndCallback(composition, call, result));
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to load PAG from URL", e);
                            mainHandler.post(() -> result.error("-1100", "URL加载失败: " + e.getMessage(), null));
                        }
                    });
                    return null;
                }
            }, DataLoadHelper.FROM_PLUGIN);
        } else if (filePath != null && !filePath.isEmpty()) {
            fileLoadExecutor.execute(() -> {
                try {
                    PAGFile composition = loadPAGFileFromPathSafely(filePath);
                    mainHandler.post(() -> initPagPlayerAndCallback(composition, call, result));
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load PAG from file", e);
                    mainHandler.post(() -> result.error("-1100", "文件加载失败: " + e.getMessage(), null));
                }
            });
        } else {
            result.error("-1100", "未添加资源", null);
        }
    }

    // JNI安全包装器 - PAG文件加载方法
    private PAGFile loadPAGFileSafely(byte[] bytes) {
        try {
            return PAGFile.Load(bytes);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load PAG from bytes", e);
            throw new RuntimeException("PAG文件加载失败", e);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error when loading PAG from bytes", e);
            throw new RuntimeException("PAG native加载失败", e);
        }
    }

    private PAGFile loadPAGFileFromAssetSafely(String assetKey) {
        try {
            return PAGFile.Load(context.getAssets(), assetKey);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load PAG from asset", e);
            throw new RuntimeException("Asset PAG文件加载失败", e);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error when loading PAG from asset", e);
            throw new RuntimeException("Asset PAG native加载失败", e);
        }
    }

    private PAGFile loadPAGFileFromPathSafely(String filePath) {
        try {
            return PAGFile.Load(filePath);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load PAG from path", e);
            throw new RuntimeException("文件路径PAG加载失败", e);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error when loading PAG from path", e);
            throw new RuntimeException("文件路径PAG native加载失败", e);
        }
    }

    private String getAssetKey(String assetName, String flutterPackage) {
        if (registrar != null) {
            if (flutterPackage == null || flutterPackage.isEmpty()) {
                return registrar.lookupKeyForAsset(assetName);
            } else {
                return registrar.lookupKeyForAsset(assetName, flutterPackage);
            }
        } else if (flutterAssets != null) {
            if (flutterPackage == null || flutterPackage.isEmpty()) {
                return flutterAssets.getAssetFilePathByName(assetName);
            } else {
                return flutterAssets.getAssetFilePathByName(assetName, flutterPackage);
            }
        }
        return null;
    }

    private void initPagPlayerAndCallback(PAGFile composition, MethodCall call, final Result result) {
        if (composition == null) {
            result.error("-1100", "load composition is null!", null);
            return;
        }

        try {
            final int repeatCount = call.argument(_argumentRepeatCount);
            final double initProgress = call.argument(_argumentInitProgress);
            final boolean autoPlay = call.argument(_argumentAutoPlay);

            final FlutterPagPlayer pagPlayer = new FlutterPagPlayer();
            final TextureRegistry.SurfaceTextureEntry entry = textureRegistry.createSurfaceTexture();
            entryMap.put(String.valueOf(entry.id()), entry);

            // *** 关键修复：Surface相关操作必须在主线程 ***
            SurfaceTexture surfaceTexture = entry.surfaceTexture();
            surfaceTexture.setDefaultBufferSize(composition.width(), composition.height());

            final Surface surface = new Surface(surfaceTexture);

            // 在后台线程执行PAG初始化，但Surface创建留在主线程
            backgroundHandler.post(() -> {
                try {
                    initPagPlayerInternalSafe(pagPlayer, composition, entry, surface,
                            repeatCount, initProgress, autoPlay, result);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to initialize PAG player", e);
                    mainHandler.post(() -> {
                        cleanupEntry(entry);
                        safeRelease(surface::release);
                        result.error("-1100", "初始化失败: " + e.getMessage(), null);
                    });
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to create texture entry", e);
            result.error("-1100", "创建纹理失败: " + e.getMessage(), null);
        }
    }

    private void initPagPlayerInternalSafe(FlutterPagPlayer pagPlayer, PAGFile composition,
                                           TextureRegistry.SurfaceTextureEntry entry, Surface surface,
                                           int repeatCount, double initProgress, boolean autoPlay,
                                           Result result) {
        PAGSurface pagSurface = null;
        try {
            // 使用JNI安全包装器
            pagSurface = createPAGSurfaceSafely(surface);
            if (pagSurface == null) {
                throw new RuntimeException("Failed to create PAGSurface");
            }

            // 安全初始化PAGPlayer
            initPAGPlayerSafely(pagPlayer, composition, repeatCount, initProgress, entry.id());

            // 安全设置Surface
            setPAGSurfaceSafely(pagPlayer, pagSurface);

            final PAGSurface finalPagSurface = pagSurface;
            pagPlayer.setReleaseListener(new FlutterPagPlayer.ReleaseListener() {
                @Override
                public void onRelease() {
                    safeRelease(entry::release);
                    safeRelease(surface::release);
                    safeRelease(finalPagSurface::release);
                }
            });

            layerMap.put(String.valueOf(entry.id()), pagPlayer);

            // 安全执行初始flush
            flushPAGPlayerSafely(pagPlayer);

            // 准备回调数据
            final HashMap<String, Object> callback = new HashMap<>();
            callback.put(_argumentTextureId, entry.id());
            callback.put(_argumentWidth, (double) composition.width());
            callback.put(_argumentHeight, (double) composition.height());

            // 回到主线程执行autoPlay和result回调
            mainHandler.post(() -> {
                if (autoPlay) {
                    pagPlayer.start();
                }
                result.success(callback);
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize PAG player internal", e);
            // 确保资源清理
            if (pagSurface != null) {
                safeRelease(pagSurface::release);
            }
            throw e;
        }
    }

    // JNI安全包装器方法
    private PAGSurface createPAGSurfaceSafely(Surface surface) {
        try {
            return PAGSurface.FromSurface(surface);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create PAGSurface from Surface", e);
            return null;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error when creating PAGSurface", e);
            return null;
        }
    }

    private void initPAGPlayerSafely(FlutterPagPlayer pagPlayer, PAGFile composition,
                                     int repeatCount, double initProgress, long textureId) {
        try {
            pagPlayer.init(composition, repeatCount, initProgress, channel, textureId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to init PAGPlayer", e);
            throw new RuntimeException("PAGPlayer init failed", e);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error in PAGPlayer init", e);
            throw new RuntimeException("PAGPlayer native init failed", e);
        }
    }

    private void setPAGSurfaceSafely(FlutterPagPlayer pagPlayer, PAGSurface pagSurface) {
        try {
            pagPlayer.setSurface(pagSurface);
        } catch (Exception e) {
            Log.e(TAG, "Failed to set PAGSurface", e);
            throw new RuntimeException("PAGPlayer setSurface failed", e);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error when setting PAGSurface", e);
            throw new RuntimeException("PAGPlayer setSurface native failed", e);
        }
    }

    private void flushPAGPlayerSafely(FlutterPagPlayer pagPlayer) {
        try {
            pagPlayer.flush();
        } catch (Exception e) {
            Log.e(TAG, "Failed to flush PAGPlayer", e);
            // flush失败不抛异常，只记录日志
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error when flushing PAGPlayer", e);
            // flush失败不抛异常，只记录日志
        }
    }

    private void safeRelease(Runnable releaseAction) {
        try {
            releaseAction.run();
        } catch (Exception e) {
            Log.w(TAG, "Failed to release resource", e);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "Native library error during resource release", e);
        }
    }

    private void cleanupEntry(TextureRegistry.SurfaceTextureEntry entry) {
        try {
            entryMap.remove(String.valueOf(entry.id()));
            entry.release();
        } catch (Exception e) {
            Log.w(TAG, "Failed to cleanup entry", e);
        }
    }

    void start(MethodCall call) {
        FlutterPagPlayer flutterPagPlayer = getFlutterPagPlayer(call);
        if (flutterPagPlayer != null) {
            flutterPagPlayer.start();
        }
    }

    void stop(MethodCall call) {
        FlutterPagPlayer flutterPagPlayer = getFlutterPagPlayer(call);
        if (flutterPagPlayer != null) {
            flutterPagPlayer.stop();
        }
    }

    void pause(MethodCall call) {
        FlutterPagPlayer flutterPagPlayer = getFlutterPagPlayer(call);
        if (flutterPagPlayer != null) {
            flutterPagPlayer.pause();
        }
    }

    void setProgress(MethodCall call) {
        Double progressObj = call.argument(_argumentProgress);
        if (progressObj == null) return;

        double progress = progressObj;
        FlutterPagPlayer flutterPagPlayer = getFlutterPagPlayer(call);
        if (flutterPagPlayer != null) {
            flutterPagPlayer.setProgressValue(progress);
        }
    }

    void release(MethodCall call) {
        String textureId = getTextureId(call);
        FlutterPagPlayer flutterPagPlayer = layerMap.remove(textureId);
        if (flutterPagPlayer != null) {
            flutterPagPlayer.stop();
            flutterPagPlayer.release();
        }

        TextureRegistry.SurfaceTextureEntry entry = entryMap.remove(textureId);
        if (entry != null) {
            safeRelease(() -> entry.release());
        }
    }

    List<String> getLayersUnderPoint(MethodCall call) {
        FlutterPagPlayer flutterPagPlayer = getFlutterPagPlayer(call);
        List<String> layerNames = new ArrayList<>();

        if (flutterPagPlayer != null) {
            try {
                Double x = call.argument(_argumentPointX);
                Double y = call.argument(_argumentPointY);
                if (x != null && y != null) {
                    PAGLayer[] layers = getLayersUnderPointSafely(flutterPagPlayer, x.floatValue(), y.floatValue());
                    if (layers != null) {
                        for (PAGLayer layer : layers) {
                            layerNames.add(layer.layerName());
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get layers under point", e);
            }
        }
        return layerNames;
    }

    private PAGLayer[] getLayersUnderPointSafely(FlutterPagPlayer pagPlayer, float x, float y) {
        try {
            return pagPlayer.getLayersUnderPoint(x, y);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get layers under point", e);
            return null;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error when getting layers under point", e);
            return null;
        }
    }

    FlutterPagPlayer getFlutterPagPlayer(MethodCall call) {
        return layerMap.get(getTextureId(call));
    }

    String getTextureId(MethodCall call) {
        Object textureId = call.argument(_argumentTextureId);
        return textureId != null ? String.valueOf(textureId) : null;
    }

    public void onDestroy() {
        // 清理所有资源
        for (FlutterPagPlayer pagPlayer : layerMap.values()) {
            try {
                pagPlayer.release();
            } catch (Exception e) {
                Log.w(TAG, "Failed to release PAG player", e);
            }
        }
        for (TextureRegistry.SurfaceTextureEntry entry : entryMap.values()) {
            safeRelease(() -> entry.release());
        }
        layerMap.clear();
        entryMap.clear();

        // 清理线程
        if (backgroundHandler != null) {
            backgroundHandler.removeCallbacksAndMessages(null);
        }
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Interrupted while waiting for background thread to finish");
            }
        }
        if (fileLoadExecutor != null) {
            fileLoadExecutor.shutdown();
            try {
                if (!fileLoadExecutor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                    fileLoadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                fileLoadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (channel != null) {
            channel.setMethodCallHandler(null);
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        onDestroy();
    }
}