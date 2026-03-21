package org.javacs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.javacs.completion.PruneMethodBodies;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class BenchmarkPruner {

    @State(Scope.Benchmark)
    public static class CompilerState {
        public java.nio.file.Path file;
        public String plainContents;
        public String prunedContents;
        public JavaCompilerService compiler;
        private long version;

        @Setup(org.openjdk.jmh.annotations.Level.Trial)
        public void setup() throws IOException {
            FileStore.reset();
            quietBenchmarkLogging();

            file = Paths.get("src/main/java/org/javacs/InferConfig.java").normalize();
            plainContents = Files.readString(file);
            compiler = createCompiler();

            var task = compiler.parse(source(plainContents));
            prunedContents = new PruneMethodBodies(task.task).scan(task.root, 11222L).toString();
        }

        public SourceFileObject plainSource() {
            return source(plainContents);
        }

        public SourceFileObject prunedSource() {
            return source(prunedContents);
        }

        private SourceFileObject source(String contents) {
            return new SourceFileObject(file, contents, ++version);
        }

        private static JavaCompilerService createCompiler() {
            LOG.info("Create new compiler...");

            var workspaceRoot = Paths.get(".").normalize().toAbsolutePath();
            FileStore.setWorkspaceRoots(Set.of(workspaceRoot));
            var classPath = new InferConfig(workspaceRoot).classPath();
            return new JavaCompilerService(classPath, Collections.emptySet(), Collections.emptySet(), List.of());
        }

        private static void quietBenchmarkLogging() {
            Main.setRootFormat();
            Logger.getLogger("").setLevel(java.util.logging.Level.WARNING);
            Logger.getLogger("main").setLevel(java.util.logging.Level.WARNING);
        }
    }

    @Benchmark
    public void parsePlain(CompilerState state, Blackhole blackhole) {
        var parse = Parser.parseJavaFileObject(state.plainSource());
        blackhole.consume(parse.root);
    }

    @Benchmark
    public void compilePruned(CompilerState state, Blackhole blackhole) {
        try (var compile = state.compiler.compile(List.of(state.prunedSource()))) {
            blackhole.consume(compile.root());
        }
    }

    @Benchmark
    public void compilePlain(CompilerState state, Blackhole blackhole) {
        try (var compile = state.compiler.compile(List.of(state.plainSource()))) {
            blackhole.consume(compile.root());
        }
    }

    private static final Logger LOG = Logger.getLogger("main");
}
