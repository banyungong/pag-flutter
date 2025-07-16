package com.example.flutter_pag_plugin;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.animation.LinearInterpolator;

import org.libpag.PAGFile;
import org.libpag.PAGPlayer;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import io.flutter.plugin.common.MethodChannel;

public class FlutterPagPlayer extends PAGPlayer {
    private static final String TAG = "FlutterPagPlayer";

    private final ValueAnimator animator = ValueAnimator.ofFloat(0.0F, 1.0F);
    private boolean isRelease;
    private long currentPlayTime = 0L;
    private double progress = 0;
    private double initProgress = 0;
    private ReleaseListener releaseListener;

    private MethodChannel channel;
    private long textureId;

    // 后台渲染线程
    private HandlerThread renderThread;
    private Handler renderHandler;
    private Handler mainHandler;

    // 防止重复操作的标志
    private final AtomicBoolean isRenderPending = new AtomicBoolean(false);
    private final AtomicBoolean isProgressPending = new AtomicBoolean(false);

    // 帧率控制
    private long lastRenderTime = 0;
    private static final long MIN_RENDER_INTERVAL = 16; // 约60fps

    public void init(PAGFile file, int repeatCount, double initProgress, MethodChannel channel, long textureId) {
        this.channel = channel;
        this.textureId = textureId;
        this.progress = initProgress;
        this.initProgress = initProgress;

        // 初始化线程
        initRenderThread();

        // 在后台线程中执行可能阻塞的初始化操作
        executeOnRenderThread(() -> {
            try {
                safeSetComposition(file);

                Log.d(TAG, "setComposition completed");
            } catch (Exception e) {
                Log.e(TAG, "setComposition failed: " + e.getMessage());
            }
        });

        // 在主线程初始化动画器
        if (mainHandler != null) {
            mainHandler.post(() -> initAnimator(repeatCount));
        } else {
            initAnimator(repeatCount);
        }
    }

    private void initRenderThread() {
        renderThread = new HandlerThread("PAGRenderThread");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
    }

    private void initAnimator(int repeatCount) {
        animator.setDuration(duration() / 1000L);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animatorUpdateListener);
        animator.addListener(animatorListenerAdapter);
        if (repeatCount < 0) {
            repeatCount = 0;
        }
        animator.setRepeatCount(repeatCount - 1);
        setProgressValue(initProgress);
    }

    public void setProgressValue(double value) {
        this.progress = Math.max(0.0D, Math.min(value, 1.0D));
        this.currentPlayTime = (long) (progress * (double) this.animator.getDuration());
        this.animator.setCurrentPlayTime(currentPlayTime);

        // 异步执行setProgress和flush
        executeProgressAndFlush(progress);
    }

    public void start() {
        animator.start();
    }

    public void stop() {
        pause();
        setProgressValue(initProgress);
    }

    public void pause() {
        animator.pause();
    }

    @Override
    public void release() {
        isRelease = true;

        // 清理动画器
        animator.removeUpdateListener(animatorUpdateListener);
        animator.removeListener(animatorListenerAdapter);

        // 在后台线程执行release
        executeOnRenderThread(() -> {
            try {
                safeRelease();
                Log.d(TAG, "PAGPlayer released");
            } catch (Exception e) {
                Log.e(TAG, "Release failed: " + e.getMessage());
            } finally {
                // 回到主线程执行回调
                if (mainHandler != null) {
                    mainHandler.post(() -> {
                        if (releaseListener != null) {
                            releaseListener.onRelease();
                        }
                    });
                }
            }
        });

        // 清理渲染线程
        cleanupRenderThread();
    }

    private void cleanupRenderThread() {
        if (renderHandler != null) {
            renderHandler.removeCallbacksAndMessages(null);
        }
        if (renderThread != null) {
            renderThread.quitSafely();
            try {
                renderThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Interrupted while waiting for render thread to finish");
            }
        }
    }

    @Override
    public boolean flush() {
        if (isRelease) {
            return false;
        }

        // 如果在主线程被调用，转到后台线程执行
        if (Looper.myLooper() == Looper.getMainLooper()) {
            executeFlush();
            return true; // 假设成功，避免阻塞主线程
        } else {
            // 已经在后台线程，直接执行
            return safeFlush();
        }
    }

    // JNI安全包装器方法
    private void safeSetComposition(PAGFile file) {
        try {
            setComposition(file);
        } catch (Exception e) {
            Log.e(TAG, "setComposition failed: " + e.getMessage());
            throw new RuntimeException("setComposition failed", e);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error in setComposition: " + e.getMessage());
            throw new RuntimeException("setComposition native failed", e);
        }
    }

    private boolean safeFlush() {
        try {
            return super.flush();
        } catch (Exception e) {
            Log.e(TAG, "Flush failed: " + e.getMessage());
            return false;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error in flush: " + e.getMessage());
            return false;
        }
    }

    private void safeSetProgress(double progress) {
        try {
            setProgress(progress);
        } catch (Exception e) {
            Log.e(TAG, "SetProgress failed: " + e.getMessage());
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error in setProgress: " + e.getMessage());
        }
    }

    private void safeCacheEnabled() {
        try {
            cacheEnabled();
        } catch (Exception e) {
            Log.e(TAG, "cacheEnabled failed: " + e.getMessage());
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error in cacheEnabled: " + e.getMessage());
        }
    }

    private void safeRelease() {
        try {
            super.release();
        } catch (Exception e) {
            Log.e(TAG, "Super release failed: " + e.getMessage());
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native library error in release: " + e.getMessage());
        }
    }

    // 执行单独的flush操作
    private void executeFlush() {
        if (renderHandler == null || isRelease) return;

        if (!isRenderPending.getAndSet(true)) {
            renderHandler.post(() -> {
                try {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastRenderTime >= MIN_RENDER_INTERVAL) {
                        safeFlush();
                        lastRenderTime = currentTime;
                    }
                } finally {
                    isRenderPending.set(false);
                }
            });
        }
    }

    // 执行setProgress + flush的组合操作
    private void executeProgressAndFlush(double progress) {
        if (renderHandler == null || isRelease) return;

        if (!isProgressPending.getAndSet(true)) {
            renderHandler.post(() -> {
                try {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastRenderTime >= MIN_RENDER_INTERVAL) {
                        safeSetProgress(progress);
                        safeFlush();
                        lastRenderTime = currentTime;
                    }
                } finally {
                    isProgressPending.set(false);
                }
            });
        }
    }

    // 通用的后台线程执行方法
    private void executeOnRenderThread(Runnable task) {
        if (renderHandler != null && !isRelease) {
            renderHandler.post(task);
        }
    }

    // 动画更新监听器 - 现在是ANR安全的
    private final ValueAnimator.AnimatorUpdateListener animatorUpdateListener = new ValueAnimator.AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            progress = (double) (Float) animation.getAnimatedValue();
            currentPlayTime = (long) (progress * (double) animator.getDuration());

            // 异步执行可能阻塞的操作
            executeProgressAndFlush(progress);
        }
    };

    public void setReleaseListener(ReleaseListener releaseListener) {
        this.releaseListener = releaseListener;
    }

    public interface ReleaseListener {
        void onRelease();
    }

    // 动画状态监听
    private final AnimatorListenerAdapter animatorListenerAdapter = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animator) {
            super.onAnimationStart(animator);
            notifyEvent(FlutterPagPlugin._eventStart);
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            super.onAnimationEnd(animation);
            int repeatCount = ((ValueAnimator) animation).getRepeatCount();
            if (repeatCount >= 0 && (animation.getDuration() > 0) &&
                    (currentPlayTime / animation.getDuration() > repeatCount)) {
                notifyEvent(FlutterPagPlugin._eventEnd);
            }
        }

        @Override
        public void onAnimationCancel(Animator animator) {
            super.onAnimationCancel(animator);
            notifyEvent(FlutterPagPlugin._eventCancel);
        }

        @Override
        public void onAnimationRepeat(Animator animator) {
            super.onAnimationRepeat(animator);
            notifyEvent(FlutterPagPlugin._eventRepeat);
        }
    };

    void notifyEvent(String event) {
        // 确保事件通知在主线程执行
        if (mainHandler != null) {
            mainHandler.post(() -> {
                try {
                    final HashMap<String, Object> arguments = new HashMap<>();
                    arguments.put(FlutterPagPlugin._argumentTextureId, textureId);
                    arguments.put(FlutterPagPlugin._argumentEvent, event);
                    channel.invokeMethod(FlutterPagPlugin._playCallback, arguments);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to notify event: " + event, e);
                }
            });
        }
    }
}