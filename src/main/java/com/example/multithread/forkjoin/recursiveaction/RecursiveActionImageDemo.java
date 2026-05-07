package com.example.multithread.forkjoin.recursiveaction;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * 知识点：RecursiveAction。
 *
 * RecursiveAction 适合“没有返回值，但会修改共享数据或执行副作用”的分治任务，例如：
 * 1. 批量更新数组内容。
 * 2. 并行处理图片像素。
 * 3. 批量写入已经按区间切分的数据。
 *
 * 本示例把一组灰度值做“反色”处理：newValue = 255 - oldValue。
 *
 * 运行方式：
 * java -cp target/classes com.example.multithread.forkjoin.recursiveaction.RecursiveActionImageDemo
 */
public class RecursiveActionImageDemo {

    public static void main(String[] args) {
        int[] grayscalePixels = {
                0, 20, 40, 60, 80, 100, 120, 140,
                160, 180, 200, 220, 240, 255
        };

        log("处理前 = " + Arrays.toString(grayscalePixels));
        try (ForkJoinPool pool = new ForkJoinPool(4)) {
            pool.invoke(new InvertPixelTask(grayscalePixels, 0, grayscalePixels.length));
        }
        log("处理后 = " + Arrays.toString(grayscalePixels));
    }

    private static class InvertPixelTask extends RecursiveAction {
        private static final int THRESHOLD = 4;

        private final int[] pixels;
        private final int start;
        private final int end;

        private InvertPixelTask(int[] pixels, int start, int end) {
            this.pixels = pixels;
            this.start = start;
            this.end = end;
        }

        @Override
        protected void compute() {
            if (end - start <= THRESHOLD) {
                for (int i = start; i < end; i++) {
                    pixels[i] = 255 - pixels[i];
                }
                log("直接处理像素区间 [" + start + ", " + end + ")");
                return;
            }

            int middle = (start + end) / 2;
            invokeAll(
                    new InvertPixelTask(pixels, start, middle),
                    new InvertPixelTask(pixels, middle, end)
            );
        }
    }

    private static void log(String msg) {
        System.out.printf("%s [%s] %s%n", LocalTime.now(), Thread.currentThread().getName(), msg);
    }
}
