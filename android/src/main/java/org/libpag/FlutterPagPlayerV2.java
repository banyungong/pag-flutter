package org.libpag;


import android.content.Context;

import com.example.flutter_pag_plugin.FlutterPagPlugin;
import com.example.flutter_pag_plugin.IPagPlayer;

import java.util.HashMap;

import io.flutter.Log;
import io.flutter.plugin.common.MethodChannel;


public class FlutterPagPlayerV2 extends PAGPlayer implements PAGAnimator.Listener, IPagPlayer {

    private PAGAnimator animator;
    private boolean isRelease;
    private double progress = 0;
    private double initProgress = 0;
    private IPagPlayer.ReleaseListener releaseListener;

    private MethodChannel channel;
    private long textureId;

    public void init(Context context, PAGFile file, int repeatCount, double initProgress, MethodChannel channel, long textureId) {
        animator = PAGAnimator.a(context, this);
        setComposition(file);
        this.channel = channel;
        this.textureId = textureId;
        progress = initProgress;
        this.initProgress = initProgress;
        initAnimator(repeatCount);
    }

    private void initAnimator(int repeatCount) {
        animator.setDuration(duration());
        if (repeatCount < 0) {
            repeatCount = 0;
        }
        animator.setRepeatCount(repeatCount);
        setProgressValue(initProgress);
    }

    public void setProgressValue(double value) {
        if (isRelease) {
            return;
        }
        try {
            this.progress = Math.max(0.0D, Math.min(value, 1.0D));
            if (animator != null) {
                this.animator.setProgress(progress);
            }
            setProgress(progress);
            flush();
        } catch (Exception e) {
            Log.e("FlutterPagPlayerV2", "Error in setProgressValue: " + e.getMessage(), e);
        }
    }

    public void start() {
        if (isRelease || animator == null) {
            return;
        }
        try {
            animator.a();
        } catch (Exception e) {
            Log.e("FlutterPagPlayerV2", "Error in start: " + e.getMessage(), e);
        }
    }

    public void stop() {
        if (isRelease) {
            return;
        }
        try {
            pause();
            setProgressValue(initProgress);
        } catch (Exception e) {
            Log.e("FlutterPagPlayerV2", "Error in stop: " + e.getMessage(), e);
        }
    }

    public void pause() {
        if (isRelease || animator == null) {
            return;
        }
        try {
            animator.cancel();
        } catch (Exception e) {
            Log.e("FlutterPagPlayerV2", "Error in pause: " + e.getMessage(), e);
        }
    }

    @Override
    public void release() {
        // 防止重复释放
        if (isRelease) {
            return;
        }
        isRelease = true;
        
        // 先停止动画器，防止在释放过程中动画回调继续执行
        // 设置 isRelease = true 后，即使回调还在执行，也会在回调开始处直接返回
        try {
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
        } catch (Exception e) {
            Log.e("FlutterPagPlayerV2", "Error canceling animator in release: " + e.getMessage(), e);
        }
        
        super.release();
        if (releaseListener != null) {
            try {
                releaseListener.onRelease();
                // 释放后清空 listener，避免重复调用
                releaseListener = null;
            } catch (Exception e) {
                Log.e("FlutterPagPlayerV2", "Error in releaseListener: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean flush() {
        if (isRelease) {
            return false;
        }
        return super.flush();
    }

    @Override
    public void setReleaseListener(IPagPlayer.ReleaseListener releaseListener) {
        this.releaseListener = releaseListener;
    }

    @Override
    public void onAnimationStart(PAGAnimator pagAnimator) {
        if (isRelease) {
            return;
        }
        try {
//        Log.e("FlutterPagPlayerV2", "onAnimationStart name:"+getComposition().layerName());
            notifyEvent(FlutterPagPlugin._eventStart);
        } catch (Exception e) {
            Log.e("FlutterPagPlayerV2", "Error in onAnimationStart: " + e.getMessage(), e);
        }
    }

    @Override
    public void onAnimationEnd(PAGAnimator pagAnimator) {
        if (isRelease) {
            return;
        }
        try {
//        Log.e("FlutterPagPlayerV2", "onAnimationEnd name:" +getComposition().layerName());
//        Log.e("FlutterPagPlayerV2", "onAnimationEnd repeatCount:" + repeatCount);
//        Log.e("FlutterPagPlayerV2", "onAnimationEnd duration:" + pagAnimator.duration());
            notifyEvent(FlutterPagPlugin._eventEnd);
        } catch (Exception e) {
            Log.e("FlutterPagPlayerV2", "Error in onAnimationEnd: " + e.getMessage(), e);
        }
    }

    @Override
    public void onAnimationCancel(PAGAnimator pagAnimator) {
        if (isRelease) {
            return;
        }
        try {
            notifyEvent(FlutterPagPlugin._eventCancel);
        } catch (Exception e) {
            Log.e("FlutterPagPlayerV2", "Error in onAnimationCancel: " + e.getMessage(), e);
        }
    }

    @Override
    public void onAnimationRepeat(PAGAnimator pagAnimator) {
        if (isRelease) {
            return;
        }
        try {
            notifyEvent(FlutterPagPlugin._eventRepeat);
        } catch (Exception e) {
            Log.e("FlutterPagPlayerV2", "Error in onAnimationRepeat: " + e.getMessage(), e);
        }
    }

    @Override
    public void onAnimationUpdate(PAGAnimator pagAnimator) {
        // 检查是否已释放，避免在释放过程中执行操作
        if (isRelease) {
            return;
        }
        try {
            progress = pagAnimator.progress();
            setProgress(progress);
            flush();
        } catch (Exception e) {
            Log.e("FlutterPagPlayerV2", "Error in onAnimationUpdate: " + e.getMessage(), e);
        }
    }

    void notifyEvent(String event) {
        final HashMap<String, Object> arguments = new HashMap<>();
        arguments.put(FlutterPagPlugin._argumentTextureId, textureId);
        arguments.put(FlutterPagPlugin._argumentEvent, event);
        channel.invokeMethod(FlutterPagPlugin._playCallback, arguments);
    }
}
