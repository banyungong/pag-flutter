package org.libpag;


import android.animation.ValueAnimator;
import android.content.Context;

import com.example.flutter_pag_plugin.FlutterPagPlugin;

import java.util.HashMap;

import io.flutter.Log;
import io.flutter.plugin.common.MethodChannel;


public class FlutterPagPlayerV2 extends PAGPlayer implements PAGAnimator.Listener {

    private PAGAnimator animator;
    private boolean isRelease;
    private long currentPlayTime = 0L;
    private double progress = 0;
    private double initProgress = 0;
    private ReleaseListener releaseListener;

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
        animator.setRepeatCount(repeatCount - 1);
        setProgressValue(initProgress);
    }

    public void setProgressValue(double value) {
        this.progress = Math.max(0.0D, Math.min(value, 1.0D));
        this.animator.setProgress(progress);
        setProgress(progress);
        flush();
    }

    public void start() {
        animator.a();
    }

    public void stop() {
        pause();
        setProgressValue(initProgress);
    }

    public void pause() {
        animator.cancel();
    }

    @Override
    public void release() {
        super.release();
        if (releaseListener != null) {
            releaseListener.onRelease();
        }
        isRelease = true;
    }

    @Override
    public boolean flush() {
        if (isRelease) {
            return false;
        }
        return super.flush();
    }

    public void setReleaseListener(ReleaseListener releaseListener) {
        this.releaseListener = releaseListener;
    }

    @Override
    public void onAnimationStart(PAGAnimator pagAnimator) {
        notifyEvent(FlutterPagPlugin._eventStart);
    }

    @Override
    public void onAnimationEnd(PAGAnimator pagAnimator) {
        int repeatCount = pagAnimator.repeatCount();
        if (repeatCount >= 0 && (pagAnimator.duration() > 0) &&
                (currentPlayTime / pagAnimator.duration() > repeatCount)) {
            notifyEvent(FlutterPagPlugin._eventEnd);
        }
    }

    @Override
    public void onAnimationCancel(PAGAnimator pagAnimator) {
        notifyEvent(FlutterPagPlugin._eventCancel);

    }

    @Override
    public void onAnimationRepeat(PAGAnimator pagAnimator) {
        notifyEvent(FlutterPagPlugin._eventRepeat);
    }

    @Override
    public void onAnimationUpdate(PAGAnimator pagAnimator) {
        //获取当前线程名
        progress = pagAnimator.progress();
        currentPlayTime = (long) (progress * (double) animator.duration());
        setProgress(progress);
        flush();
    }

    public interface ReleaseListener {
        void onRelease();
    }

    void notifyEvent(String event) {
        final HashMap<String, Object> arguments = new HashMap<>();
        arguments.put(FlutterPagPlugin._argumentTextureId, textureId);
        arguments.put(FlutterPagPlugin._argumentEvent, event);
        channel.invokeMethod(FlutterPagPlugin._playCallback, arguments);
    }
}
