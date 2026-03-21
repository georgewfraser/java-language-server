package org.javacs;

import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Logger;
import org.javacs.debug.*;
import org.javacs.debug.proto.*;
import org.junit.Test;

public class JavaDebugServerTest {
    Path workingDirectory = Paths.get("src/test/examples/debug");
    DebugClient client = new MockClient();
    JavaDebugServer server = new JavaDebugServer(client);
    Process process;
    ArrayBlockingQueue<StoppedEventBody> stoppedEvents = new ArrayBlockingQueue<>(10);

    class MockClient implements DebugClient {
        @Override
        public void initialized() {}

        @Override
        public void stopped(StoppedEventBody evt) {
            stoppedEvents.add(evt);
        }

        @Override
        public void terminated(TerminatedEventBody evt) {}

        @Override
        public void exited(ExitedEventBody evt) {}

        @Override
        public void output(OutputEventBody evt) {
            LOG.info(evt.output);
        }

        @Override
        public void breakpoint(BreakpointEventBody evt) {
            if (evt.breakpoint.verified) {
                LOG.info(
                        String.format(
                                "Breakpoint at %s:%d is verified", evt.breakpoint.source.path, evt.breakpoint.line));
            } else {
                LOG.info(
                        String.format(
                                "Breakpoint at %s:%d cannot be verified because %s",
                                evt.breakpoint.source.path, evt.breakpoint.line, evt.breakpoint.message));
            }
        }

        @Override
        public RunInTerminalResponseBody runInTerminal(RunInTerminalRequest req) {
            throw new UnsupportedOperationException();
        }
    }

    public void launchProcess(String mainClass) throws IOException, InterruptedException {
        var command =
                List.of("java", "-Xdebug", "-Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y", mainClass);
        LOG.info("Launch " + String.join(", ", command));
        process = new ProcessBuilder().command(command).directory(workingDirectory.toFile()).inheritIO().start();
        java.lang.Thread.sleep(1000);
    }

    private void attach(int port) {
        var attach = new AttachRequestArguments();
        attach.port = port;
        attach.sourceRoots = new String[] {};
        server.attach(attach);
    }

    private void setBreakpoint(String className, int line) {
        var set = new SetBreakpointsArguments();
        var point = new SourceBreakpoint();
        point.line = line;
        set.source.path = workingDirectory.resolve(className + ".java").toString();
        set.breakpoints = new SourceBreakpoint[] {point};
        server.setBreakpoints(set);
    }

    private static void setVm(JavaDebugServer server, VirtualMachine vm) throws ReflectiveOperationException {
        Field field = JavaDebugServer.class.getDeclaredField("vm");
        field.setAccessible(true);
        field.set(server, vm);
    }

    @SuppressWarnings("unchecked")
    private static List<Breakpoint> pendingBreakpoints(JavaDebugServer server) throws ReflectiveOperationException {
        Field field = JavaDebugServer.class.getDeclaredField("pendingBreakpoints");
        field.setAccessible(true);
        return (List<Breakpoint>) field.get(server);
    }

    private static Method privateMethod(String name, Class<?>... parameterTypes) throws ReflectiveOperationException {
        Method method = JavaDebugServer.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static VirtualMachine fakeVm(String defaultStratum, List<ReferenceType> allClasses) {
        return (VirtualMachine)
                Proxy.newProxyInstance(
                        VirtualMachine.class.getClassLoader(),
                        new Class<?>[] {VirtualMachine.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "allClasses":
                                    return allClasses;
                                case "getDefaultStratum":
                                    return defaultStratum;
                                case "toString":
                                    return "fakeVm";
                                default:
                                    throw new UnsupportedOperationException(method.getName());
                            }
                        });
    }

    private static ReferenceType fakeType(String name, String sourcePath) {
        return (ReferenceType)
                Proxy.newProxyInstance(
                        ReferenceType.class.getClassLoader(),
                        new Class<?>[] {ReferenceType.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "name":
                                    return name;
                                case "sourcePaths":
                                    return List.of(sourcePath);
                                case "toString":
                                    return name;
                                default:
                                    throw new UnsupportedOperationException(method.getName());
                            }
                        });
    }

    private static ReferenceType fakeIrrelevantType(String name) {
        return (ReferenceType)
                Proxy.newProxyInstance(
                        ReferenceType.class.getClassLoader(),
                        new Class<?>[] {ReferenceType.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "name":
                                    return name;
                                case "sourcePaths":
                                    throw new AssertionError("Irrelevant classes should not need source paths");
                                case "toString":
                                    return name;
                                default:
                                    throw new UnsupportedOperationException(method.getName());
                            }
                        });
    }

    private static <T> T invoke(Method method, Object target, Object... args) throws ReflectiveOperationException {
        try {
            @SuppressWarnings("unchecked")
            T result = (T) method.invoke(target, args);
            return result;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw e;
        }
    }

    @Test
    public void attachToProcess() throws IOException, InterruptedException {
        launchProcess("Hello");
        attach(5005);
        server.configurationDone();
        process.waitFor();
    }

    @Test
    public void loadedTypesMatchingSkipsIrrelevantClasses() throws ReflectiveOperationException {
        var relevant = fakeType("com.example.Hello", "com/example/Hello.java");
        var irrelevant = fakeIrrelevantType("org.other.Bar");
        setVm(server, fakeVm("Java", List.of(relevant, irrelevant)));

        var loadedTypesMatching = privateMethod("loadedTypesMatching", String.class);
        List<ReferenceType> matches = invoke(loadedTypesMatching, server, "/tmp/src/com/example/Hello.java");
        org.junit.Assert.assertEquals(1, matches.size());
        org.junit.Assert.assertSame(relevant, matches.get(0));
    }

    @Test
    public void enablePendingBreakpointsInLoadedClassesSkipsIrrelevantClasses() throws ReflectiveOperationException {
        var pending = new Breakpoint();
        pending.source = new Source();
        pending.source.path = "/tmp/src/com/example/Hello.java";
        pending.line = 4;
        pendingBreakpoints(server).add(pending);
        setVm(server, fakeVm("Java", List.of(fakeIrrelevantType("org.other.Bar"))));

        invoke(privateMethod("enablePendingBreakpointsInLoadedClasses"), server);

        org.junit.Assert.assertEquals(1, pendingBreakpoints(server).size());
    }

    @Test
    public void setBreakpoint() throws IOException, InterruptedException {
        launchProcess("Hello");
        // Attach to the process
        attach(5005);
        // Set a breakpoint at Hello.java:4
        setBreakpoint("Hello", 4);
        server.configurationDone();
        // Wait for stop
        stoppedEvents.take();
        // Find the main thread
        var threads = server.threads().threads;
        for (var t : threads) {
            if (t.name.equals("main")) {
                // Get the stack trace
                var requestTrace = new StackTraceArguments();
                requestTrace.threadId = t.id;
                var stack = server.stackTrace(requestTrace);
                System.out.println("Thread main:");
                for (var frame : stack.stackFrames) {
                    System.out.println(String.format("\t%s:%d (%s)", frame.name, frame.line, frame.source.path));
                }
                // Get variables
                var requestScopes = new ScopesArguments();
                requestScopes.frameId = stack.stackFrames[0].id;
                var scopes = server.scopes(requestScopes).scopes;
                // Get locals
                var requestLocals = new VariablesArguments();
                requestLocals.variablesReference = scopes[0].variablesReference;
                var locals = server.variables(requestLocals).variables;
                System.out.println("Locals:");
                for (var v : locals) {
                    System.out.println(String.format("\t%s %s = %s", v.type, v.name, v.value));
                }
                // Get arguments
                var requestArgs = new VariablesArguments();
                requestArgs.variablesReference = scopes[1].variablesReference;
                var arguments = server.variables(requestArgs).variables;
                System.out.println("Arguments:");
                for (var v : arguments) {
                    System.out.println(String.format("\t%s %s = %s", v.type, v.name, v.value));
                }
            }
        }
        // Wait for process to exit
        server.continue_(new ContinueArguments());
        process.waitFor();
    }

    @Test
    public void step() throws IOException, InterruptedException {
        launchProcess("Hello");
        // Attach to the process
        attach(5005);
        // Set a breakpoint at HelloWorld.java:4
        setBreakpoint("Hello", 4);
        server.configurationDone();
        // Wait for stop
        stoppedEvents.take();
        // Find the main thread
        var threads = server.threads().threads;
        for (var t : threads) {
            if (t.name.equals("main")) {
                var next = new NextArguments();
                next.threadId = t.id;
                server.next(next);
                // Wait for stop
                stoppedEvents.take();
            }
        }
        // Wait for process to exit
        server.continue_(new ContinueArguments());
        process.waitFor();
    }

    @Test
    public void pause() throws IOException, InterruptedException {
        launchProcess("Hello");
        attach(5005);
        server.configurationDone();

        var pause = new PauseArguments();
        server.pause(pause);

        var stopped = stoppedEvents.take();
        org.junit.Assert.assertEquals("pause", stopped.reason);
        org.junit.Assert.assertTrue(stopped.allThreadsStopped);

        server.continue_(new ContinueArguments());
        process.waitFor();
    }

    @Test
    public void addBreakpoint() throws IOException, InterruptedException {
        launchProcess("Hello");
        // Attach to the process
        attach(5005);
        // Stop at 4
        setBreakpoint("Hello", 4);
        server.configurationDone();
        stoppedEvents.take();
        // Stop at 6
        setBreakpoint("Hello", 6);
        server.continue_(new ContinueArguments());
        stoppedEvents.take();
        // Wait for process to exit
        server.continue_(new ContinueArguments());
        process.waitFor();
    }

    @Test
    public void printCollections() throws IOException, InterruptedException {
        launchProcess("Collections");
        attach(5005);
        setBreakpoint("Collections", 8);
        server.configurationDone();
        stoppedEvents.take();
        // Find the main thread
        var threads = server.threads().threads;
        for (var t : threads) {
            if (t.name.equals("main")) {
                // Get the stack trace
                var requestTrace = new StackTraceArguments();
                requestTrace.threadId = t.id;
                var stack = server.stackTrace(requestTrace);
                System.out.println("Thread main:");
                for (var frame : stack.stackFrames) {
                    System.out.println(String.format("\t%s:%d (%s)", frame.name, frame.line, frame.source.path));
                }
                // Get variables
                var requestScopes = new ScopesArguments();
                requestScopes.frameId = stack.stackFrames[0].id;
                var scopes = server.scopes(requestScopes).scopes;
                // Get locals
                var requestLocals = new VariablesArguments();
                requestLocals.variablesReference = scopes[0].variablesReference;
                var locals = server.variables(requestLocals).variables;
                System.out.println("Locals:");
                for (var v : locals) {
                    System.out.println(String.format("\t%s %s = %s", v.type, v.name, v.value));
                }
            }
        }
        // Wait for process to exit
        server.continue_(new ContinueArguments());
        process.waitFor();
    }

    private static final Logger LOG = Logger.getLogger("main");
}
