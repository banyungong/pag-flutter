package com.example.flutter_pag_plugin;

import org.libpag.PAGLayer;

/**
 * PAG Player 统一接口
 * 用于统一 FlutterPagPlayer 和 FlutterPagPlayerV2 的使用
 */
public interface IPagPlayer {
    /**
     * 开始播放
     */
    void start();

    /**
     * 停止播放
     */
    void stop();

    /**
     * 暂停播放
     */
    void pause();

    /**
     * 设置播放进度
     * @param value 进度值，范围 0.0 - 1.0
     */
    void setProgressValue(double value);

    /**
     * 释放资源
     */
    void release();

    /**
     * 刷新渲染
     */
    boolean flush();

    /**
     * 设置释放监听器
     * @param releaseListener 释放监听器
     */
    void setReleaseListener(ReleaseListener releaseListener);

    /**
     * 获取指定点下的图层
     * @param x X 坐标
     * @param y Y 坐标
     * @return 图层数组
     */
    PAGLayer[] getLayersUnderPoint(float x, float y);

    /**
     * 释放监听器接口
     */
    interface ReleaseListener {
        void onRelease();
    }
}
