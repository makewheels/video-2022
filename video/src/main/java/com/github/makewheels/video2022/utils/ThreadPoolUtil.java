package com.github.makewheels.video2022.utils;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 线程池工具类
 */
@Slf4j
public class ThreadPoolUtil {
    private static ThreadPoolExecutor executor;

    private ThreadPoolUtil() {
    }

    public static void initThreadPool() {
        int corePoolSize = Math.max(RuntimeUtil.getProcessorCount() / 2, 1);
        executor = new ThreadPoolExecutor(
                corePoolSize, corePoolSize, 1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    static {
        initThreadPool();
    }

    public static <T> List<T> submitTasks(List<Callable<T>> tasks) {
        List<CompletableFuture<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            CompletableFuture<T> future = new CompletableFuture<>();
            futures.add(future);
            if (executor.getQueue().remainingCapacity() < executor.getQueue().size() / 10
                    || executor.getPoolSize() >= executor.getMaximumPoolSize()) {
                ThreadUtil.sleep(RandomUtil.randomInt(100, 500));
            }

            executor.submit(() -> {
                try {
                    T result = task.call();
                    future.complete(result);
                } catch (Exception e) {
                    log.error(ExceptionUtils.getStackTrace(e));
                    future.completeExceptionally(e);
                }
            });
        }

        executor.shutdown();
        return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());
    }

}
