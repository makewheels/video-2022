package com.github.makewheels.video2022;

import com.github.makewheels.video2022.utils.ThreadPoolUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Slf4j
public class TestThreadPool {

    public static void main(String[] args) {
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int finalI = i;
            tasks.add(() -> {
                log.info("Task " + finalI + " started");
                Thread.sleep(1000);
                return finalI;
            });
        }

        List<Integer> results = ThreadPoolUtil.submitTasks(tasks);
        log.info("Results: " + results);
    }
}
