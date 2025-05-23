package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File; // For File.pathSeparator
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap; // For mock environment variables
import java.util.Map; // For mock environment variables
import java.util.Set;
import org.junit.Test;

public class InferConfigTest {
    private Path workspaceRoot = Paths.get("src/test/examples/maven-project");
    private Path mavenHome = Paths.get("src/test/examples/home-dir/.m2");
    private Path gradleHome = Paths.get("src/test/examples/home-dir/.gradle");
    private Set<String> externalDependencies = Set.of("com.external:external-library:1.2");
    private InferConfig both = new InferConfig(workspaceRoot, externalDependencies, mavenHome, gradleHome);
    private InferConfig gradle = new InferConfig(workspaceRoot, externalDependencies, Paths.get("nowhere"), gradleHome);
    private InferConfig thisProject = new InferConfig(Paths.get("."), (Map<String, String>) null); // Use null to get System.getenv()

    @Test
    public void classpathFromEnvironmentVariable() {
        String dummyPath1 = Paths.get("dummy1.jar").toAbsolutePath().toString();
        String dummyPath2 = Paths.get("dummy2.jar").toAbsolutePath().toString();
        String classpathValue = dummyPath1 + File.pathSeparator + dummyPath2;

        Map<String, String> mockEnv = new HashMap<>();
        mockEnv.put("CLASSPATH", classpathValue);
        // We also need to provide a PATH, otherwise findExecutableOnPath might fail if it's called by getMvnCommand
        // which could be called if externalDependencies is empty and CLASSPATH is also empty (though not in this specific test case)
        // For safety, let's provide a minimal PATH.
        mockEnv.put("PATH", "/usr/bin:/bin");


        InferConfig inferConfig = new InferConfig(Paths.get("."), mockEnv);
        Set<Path> expectedPaths = Set.of(Paths.get(dummyPath1), Paths.get(dummyPath2));
        Set<Path> actualPaths = inferConfig.classPath();

        assertThat(actualPaths, is(expectedPaths));
    }

    @Test
    public void mavenClassPath() {
        assertThat(
                both.classPath(),
                contains(mavenHome.resolve("repository/com/external/external-library/1.2/external-library-1.2.jar")));
        // v1.1 should be ignored
    }

    @Test
    public void gradleClasspath() {
        assertThat(
                gradle.classPath(),
                contains(
                        gradleHome.resolve(
                                "caches/modules-2/files-2.1/com.external/external-library/1.2/xxx/external-library-1.2.jar")));
        // v1.1 should be ignored
    }

    @Test
    public void mavenDocPath() {
        assertThat(
                both.buildDocPath(),
                contains(
                        mavenHome.resolve(
                                "repository/com/external/external-library/1.2/external-library-1.2-sources.jar")));
        // v1.1 should be ignored
    }

    @Test
    public void gradleDocPath() {
        assertThat(
                gradle.buildDocPath(),
                contains(
                        gradleHome.resolve(
                                "caches/modules-2/files-2.1/com.external/external-library/1.2/yyy/external-library-1.2-sources.jar")));
        // v1.1 should be ignored
    }

    @Test
    public void dependencyList() {
        assertThat(InferConfig.mvnDependencies(Paths.get("pom.xml"), "dependency:list", null), not(empty()));
    }

    @Test
    public void thisProjectClassPath() {
        // Re-initialize thisProject to ensure it uses the constructor that defaults to System.getenv()
        // or explicitly pass null for the env map.
        InferConfig currentTestProject = new InferConfig(Paths.get("."), (Map<String, String>) null);
        assertThat(
                currentTestProject.classPath(),
                hasItem(hasToString(endsWith(".m2/repository/junit/junit/4.13.1/junit-4.13.1.jar"))));
    }

    @Test
    public void thisProjectDocPath() {
        // Re-initialize thisProject for the same reasons as above.
        InferConfig currentTestProject = new InferConfig(Paths.get("."), (Map<String, String>) null);
        assertThat(
                currentTestProject.buildDocPath(),
                hasItem(hasToString(endsWith(".m2/repository/junit/junit/4.13.1/junit-4.13.1-sources.jar"))));
    }

    @Test
    public void parseDependencyLine() {
        String[][] testCases = {
            {
                "[INFO]    org.openjdk.jmh:jmh-generator-annprocess:jar:1.21:provided:/Users/georgefraser/.m2/repository/org/openjdk/jmh/jmh-generator-annprocess/1.21/jmh-generator-annprocess-1.21.jar",
                "/Users/georgefraser/.m2/repository/org/openjdk/jmh/jmh-generator-annprocess/1.21/jmh-generator-annprocess-1.21.jar",
            },
            {
                "[INFO]    org.openjdk.jmh:jmh-generator-annprocess:jar:1.21:provided:/Users/georgefraser/.m2/repository/org/openjdk/jmh/jmh-generator-annprocess/1.21/jmh-generator-annprocess-1.21.jar -- module jmh.generator.annprocess (auto)",
                "/Users/georgefraser/.m2/repository/org/openjdk/jmh/jmh-generator-annprocess/1.21/jmh-generator-annprocess-1.21.jar",
            },
        };
        for (var pair : testCases) {
            assert pair.length == 2;
            var line = pair[0];
            var expect = pair[1];
            // Note: readDependency is static and doesn't use the envVars from an InferConfig instance.
            // This test remains unaffected by the envVars changes to InferConfig.
            var path = InferConfig.readDependency(line);
            assertThat(path, equalTo(Paths.get(expect)));
        }
    }
}
