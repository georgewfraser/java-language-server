package org.javacs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class BenchmarkParser {

    @State(Scope.Benchmark)
    public static class CompilerState {
        public Path file;
        public String contents;
        private long version;

        @Setup(org.openjdk.jmh.annotations.Level.Trial)
        public void setup() throws IOException {
            FileStore.reset();
            quietBenchmarkLogging();

            file = Paths.get("src/main/java/org/javacs/JavaLanguageServer.java").normalize();
            contents = Files.readString(file);
        }

        public SourceFileObject source() {
            return new SourceFileObject(file, contents, ++version);
        }

        private static void quietBenchmarkLogging() {
            Main.setRootFormat();
            Logger.getLogger("").setLevel(java.util.logging.Level.WARNING);
            Logger.getLogger("main").setLevel(java.util.logging.Level.WARNING);
        }
    }

    @Benchmark
    public void parse(CompilerState state, Blackhole blackhole) {
        var parse = Parser.parseJavaFileObject(state.source());
        blackhole.consume(parse.root);
    }

    public static void main(String[] args) {
        var state = new CompilerState();
        try {
            state.setup();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        while (true) {
            Parser.parseJavaFileObject(state.source());
        }
    }
}
