package com.example.flutter_pag_plugin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class WorkThreadExecutor {
    private static volatile WorkThreadExecutor instance;
    private final ExecutorService singleThreadExecutor;

    private WorkThreadExecutor() {
        singleThreadExecutor = Executors.newSingleThreadExecutor();
    }

    public static WorkThreadExecutor getInstance() {
        if (instance == null) {
            synchronized (WorkThreadExecutor.class) {
                if (instance == null) {
                    instance = new WorkThreadExecutor();
                }
            }
        }
        return instance;
    }

    public void post(Runnable task) {
        singleThreadExecutor.execute(task);
    }
}