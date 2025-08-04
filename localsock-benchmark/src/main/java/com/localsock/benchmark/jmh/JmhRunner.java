package com.localsock.benchmark.jmh;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH benchmark runner for socket performance comparison.
 */
public class JmhRunner {

    public static void main(String[] args) throws RunnerException {
        System.out.println("JMH Socket Performance Benchmarks");
        System.out.println("==================================");
        System.out.println("This will take several minutes with proper JVM warmup...");
        System.out.println();

        Options opt = new OptionsBuilder()
                .include(SocketConnectionBenchmark.class.getSimpleName())
                .include(SocketThroughputBenchmark.class.getSimpleName())
                .build();

        new Runner(opt).run();
    }
}
