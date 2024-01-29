package com.github.makewheels.video2022.utils;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
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

    public static <T> List<T> submitTasks(
            List<Callable<T>> tasks, int corePoolSize, int maximumPoolSize, int workQueueCapacity) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                corePoolSize, maximumPoolSize, 1, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(workQueueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy());
        List<CompletableFuture<T>> futures = new ArrayList<>();

        for (Callable<T> task : tasks) {
            CompletableFuture<T> future = new CompletableFuture<>();
            futures.add(future);
            if (executor.getQueue().remainingCapacity() < 10) {
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

    public static <T> List<T> submitTasks(List<Callable<T>> tasks) {
        return submitTasks(tasks, 5, 10, 1000);
    }
}
