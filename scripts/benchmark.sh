
#!/usr/bin/env bash

set -euo pipefail

# Compile the benchmark
mvn test-compile

# Emit the dependencies classpath
mvn dependency:build-classpath -DincludeScope=test -Dmdep.outputFile=scripts/classpath.txt

java_args=(
    --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED
    --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
    --add-opens jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
)

# Run the benchmarks
java "${java_args[@]}" -cp "$(cat scripts/classpath.txt):target/classes:target/test-classes" org.openjdk.jmh.Main BenchmarkPruner BenchmarkParser

# Clean up
rm scripts/classpath.txt
