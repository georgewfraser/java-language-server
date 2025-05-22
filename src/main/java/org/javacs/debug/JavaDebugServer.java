package org.javacs.debug;

import com.sun.jdi.*;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.event.*;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.StepRequest;
import java.io.IOException;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.*;
import org.javacs.LogFormat;
import org.javacs.debug.proto.*;

public class JavaDebugServer implements DebugServer {
    public static void main(String[] args) { // TODO don't show references for main method
        // createLogFile();
        LOG.info(String.join(" ", args));
        new DebugAdapter(JavaDebugServer::new, System.in, System.out).run();
        System.exit(0);
    }

    private static void createLogFile() {
        try {
            // TODO make location configurable
            var logFile =
                    new FileHandler("/Users/georgefraser/Documents/java-language-server/java-debug-server.log", false);
            logFile.setFormatter(new LogFormat());
            Logger.getLogger("").addHandler(logFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final DebugClient client;
    private List<Path> sourceRoots = List.of();
    private VirtualMachine vm;
    private final List<Breakpoint> pendingBreakpoints = new ArrayList<>();
    private static int breakPointCounter = 0;

    class ReceiveVmEvents implements Runnable {
        @Override
        public void run() {
            var events = vm.eventQueue();
            while (true) {
                try {
                    var nextSet = events.remove();
                    for (var event : nextSet) {
                        process(event);
                    }
                } catch (VMDisconnectedException __) {
                    LOG.info("VM disconnected");
                    return;
                } catch (Exception e) {
                    LOG.log(Level.SEVERE, e.getMessage(), e);
                    return;
                }
            }
        }

        private void process(com.sun.jdi.event.Event event) {
            LOG.info("Received " + event.toString() + " from VM");
            if (event instanceof ClassPrepareEvent) {
                var prepare = (ClassPrepareEvent) event;
                var type = prepare.referenceType();
                LOG.info("ClassPrepareRequest for class " + type.name() + " in source " + relativePath(type));
                enablePendingBreakpointsIn(type);
                vm.resume();
            } else if (event instanceof com.sun.jdi.event.BreakpointEvent) {
                var b = (com.sun.jdi.event.BreakpointEvent) event;
                var evt = new StoppedEventBody();
                evt.reason = "breakpoint";
                evt.threadId = b.thread().uniqueID();
                evt.allThreadsStopped = b.request().suspendPolicy() == EventRequest.SUSPEND_ALL;
                client.stopped(evt);
            } else if (event instanceof StepEvent) {
                var b = (StepEvent) event;
                var evt = new StoppedEventBody();
                evt.reason = "step";
                evt.threadId = b.thread().uniqueID();
                evt.allThreadsStopped = b.request().suspendPolicy() == EventRequest.SUSPEND_ALL;
                client.stopped(evt);
                // Disable event so we can create new step events
                event.request().disable();
            } else if (event instanceof VMDeathEvent) {
                client.exited(new ExitedEventBody());
            } else if (event instanceof VMDisconnectEvent) {
                client.terminated(new TerminatedEventBody());
            }
        }
    }

    public JavaDebugServer(DebugClient client) {
        this.client = client;
        class LogToConsole extends Handler {
            private final LogFormat format = new LogFormat();

            @Override
            public void publish(LogRecord r) {
                var evt = new OutputEventBody();
                evt.category = "console";
                evt.output = format.format(r);
                client.output(evt);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        }
        Logger.getLogger("debug").addHandler(new LogToConsole());
    }

    @Override
    public Capabilities initialize(InitializeRequestArguments req) {
        var resp = new Capabilities();
        resp.supportsConfigurationDoneRequest = true;
        return resp;
    }

    @Override
    public SetBreakpointsResponseBody setBreakpoints(SetBreakpointsArguments req) {
        LOG.info("Received " + req.breakpoints.length + " breakpoints in " + req.source.path);
        disableBreakpoints(req.source);
        // Add these breakpoints to the pending set
        var resp = new SetBreakpointsResponseBody();
        resp.breakpoints = new Breakpoint[req.breakpoints.length];
        for (var i = 0; i < req.breakpoints.length; i++) {
            resp.breakpoints[i] = enableBreakpoint(req.source, req.breakpoints[i]);
        }
        return resp;
    }

    private void disableBreakpoints(Source source) {
        for (var b : vm.eventRequestManager().breakpointRequests()) {
            if (matchesFile(b, source)) {
                LOG.info(String.format("Disable breakpoint %s:%d", source.path, b.location().lineNumber()));
                b.disable();
            }
        }
    }

    private Breakpoint enableBreakpoint(Source source, SourceBreakpoint b) {
        // Check for breakpoint in disabled breakpoints
        for (var req : vm.eventRequestManager().breakpointRequests()) {
            if (matchesFile(req, source) && matchesLine(req, b.line)) {
                return enableDisabledBreakpoint(source, req);
            }
        }
        // Check for breakpoint in loaded classes
        for (var type : loadedTypesMatching(source.path)) {
            return enableBreakpointImmediately(source, b, type);
        }
        // If class hasn't been loaded, add breakpoint to pending list
        return enableBreakpointLater(source, b);
    }

    private boolean matchesFile(BreakpointRequest b, Source source) {
        try {
            var relativePath = b.location().sourcePath(vm.getDefaultStratum());
            return source.path.endsWith(relativePath);
        } catch (AbsentInformationException __) {
            LOG.warning("No source information for " + b.location());
            return false;
        }
    }

    private boolean matchesLine(BreakpointRequest b, int line) {
        return line == b.location().lineNumber(vm.getDefaultStratum());
    }

    private List<ReferenceType> loadedTypesMatching(String absolutePath) {
        var matches = new ArrayList<ReferenceType>();
        for (var type : vm.allClasses()) {
            // ReferenceType#relativePath is slow. Avoid them when possible.
            if (!mayProvideType(absolutePath, type)) {
                continue;
            }
            var path = relativePath(type);
            if (!path.isEmpty() && absolutePath.endsWith(path)) {
                matches.add(type);
            }
        }
        return matches;
    }

    private Breakpoint enableDisabledBreakpoint(Source source, BreakpointRequest b) {
        LOG.info(String.format("Enable disabled breakpoint %s:%d", source.path, b.location().lineNumber()));
        b.enable();
        var ok = new Breakpoint();
        ok.verified = true;
        ok.source = source;
        ok.line = b.location().lineNumber(vm.getDefaultStratum());
        return ok;
    }

    private Breakpoint enableBreakpointImmediately(Source source, SourceBreakpoint b, ReferenceType type) {
        if (!tryEnableBreakpointImmediately(source, b, type)) {
            var failed = new Breakpoint();
            failed.verified = false;
            failed.message = source.name + ":" + b.line + " could not be found or had no code on it";
            return failed;
        }
        var ok = new Breakpoint();
        ok.verified = true;
        ok.source = source;
        ok.line = b.line;
        return ok;
    }

    private boolean tryEnableBreakpointImmediately(Source source, SourceBreakpoint b, ReferenceType type) {
        List<Location> locations;
        try {
            locations = type.locationsOfLine(b.line);
        } catch (AbsentInformationException __) {
            LOG.info(String.format("No locations in %s for breakpoint %s:%d", type.name(), source.path, b.line));
            return false;
        }
        for (var l : locations) {
            LOG.info(String.format("Create breakpoint %s:%d", source.path, l.lineNumber()));
            var req = vm.eventRequestManager().createBreakpointRequest(l);
            req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            req.enable();
        }
        return true;
    }

    private Breakpoint enableBreakpointLater(Source source, SourceBreakpoint b) {
        LOG.info(String.format("Enable %s:%d later", source.path, b.line));
        var pending = new Breakpoint();
        pending.id = breakPointCounter++;
        pending.source = new Source();
        pending.source.path = source.path;
        pending.line = b.line;
        pending.column = b.column;
        pending.verified = false;
        pending.message = source.name + " is not yet loaded";
        pendingBreakpoints.add(pending);
        return pending;
    }

    @Override
    public SetFunctionBreakpointsResponseBody setFunctionBreakpoints(SetFunctionBreakpointsArguments req) {
        LOG.warning("Not yet implemented");
        return new SetFunctionBreakpointsResponseBody();
    }

    @Override
    public void setExceptionBreakpoints(SetExceptionBreakpointsArguments req) {
        LOG.warning("Not yet implemented");
    }

    @Override
    public void configurationDone() {
        listenForClassPrepareEvents();
        enablePendingBreakpointsInLoadedClasses();
        vm.resume();
    }

    /* Request to be notified when files with pending breakpoints are loaded */
    private void listenForClassPrepareEvents() {
        Objects.requireNonNull(vm, "vm has not been initialized");
        // Get all file names
        var distinctSourceNames = new HashSet<String>();
        for (var b : pendingBreakpoints) {
            var path = Paths.get(b.source.path);
            var name = path.getFileName();
            distinctSourceNames.add(name.toString());
        }
        // Listen for classes with those names
        for (var name : distinctSourceNames) {
            LOG.info("Listen for ClassPrepareRequest in " + name);
            var requestClassEvent = vm.eventRequestManager().createClassPrepareRequest();
            requestClassEvent.addSourceNameFilter("*" + name);
            requestClassEvent.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            requestClassEvent.enable();
        }
    }

    @Override
    public void launch(LaunchRequestArguments req) {
        throw new UnsupportedOperationException();
    }

    private static AttachingConnector connector(String transport) {
        var found = new ArrayList<String>();
        for (var conn : Bootstrap.virtualMachineManager().attachingConnectors()) {
            if (conn.transport().name().equals(transport)) {
                return conn;
            }
            found.add(conn.transport().name());
        }
        throw new RuntimeException("Couldn't find connector for transport " + transport + " in " + found);
    }

    @Override
    public void attach(AttachRequestArguments req) {
        // Remember available source roots
        sourceRoots = new ArrayList<Path>();
        for (var string : req.sourceRoots) {
            var path = Paths.get(string);
            if (!Files.exists(path)) {
                LOG.warning(string + " does not exist");
                continue;
            } else if (!Files.isDirectory(path)) {
                LOG.warning(string + " is not a directory");
                continue;
            } else {
                LOG.info(path + " is a source root");
                sourceRoots.add(path);
            }
        }
        // Attach to the running VM
        if (!tryToConnect(req.port)) {
            throw new RuntimeException("Failed to connect after 15 attempts");
        }
        // Create a thread that reads events from the VM
        var reader = new java.lang.Thread(new ReceiveVmEvents(), "receive-vm");
        reader.setDaemon(true);
        reader.start();
        // Tell the client we are ready to receive breakpoints
        client.initialized();
    }

    private boolean tryToConnect(int port) {
        var conn = connector("dt_socket");
        var args = conn.defaultArguments();
        var intervalMs = 500;
        var tryForS = 15;
        var attempts = tryForS * 1000 / intervalMs;
        args.get("port").setValue(Integer.toString(port));
        for (var attempt = 0; attempt < attempts; attempt++) {
            try {
                vm = conn.attach(args);
                return true;
            } catch (ConnectException e) {
                LOG.warning(e.getMessage());
                try {
                    java.lang.Thread.sleep(intervalMs);
                } catch (InterruptedException __) {
                    // Nothing to do
                }
            } catch (IOException | IllegalConnectorArgumentsException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    /* Set breakpoints for already-loaded classes */
    private void enablePendingBreakpointsInLoadedClasses() {
        Objects.requireNonNull(vm, "vm has not been initialized");
        for (var type : vm.allClasses()) {
            enablePendingBreakpointsIn(type);
        }
    }

    private void enablePendingBreakpointsIn(ReferenceType type) {
        // ReferenceType#relativePath is slow. Avoid them when possible.
        boolean found = false;
        for (var b : pendingBreakpoints) {
            if (mayProvideType(b.source.path, type)) {
                found = true;
                break;
            }
        }
        if (!found) {
            return;
        }

        // Check that class has source information
        var path = relativePath(type);
        if (path.isEmpty()) return;
        // Look for pending breakpoints that can be enabled
        var enabled = new ArrayList<Breakpoint>();
        for (var b : pendingBreakpoints) {
            if (b.source.path.endsWith(path)) {
                enablePendingBreakpoint(b, type);
                enabled.add(b);
            }
        }
        pendingBreakpoints.removeAll(enabled);
    }

    private void enablePendingBreakpoint(Breakpoint b, ReferenceType type) {
        try {
            var locations = type.locationsOfLine(b.line);
            for (var line : locations) {
                var req = vm.eventRequestManager().createBreakpointRequest(line);
                req.setSuspendPolicy(EventRequest.SUSPEND_ALL);
                req.enable();
            }
            if (locations.isEmpty()) {
                LOG.info("No locations at " + b.source.path + ":" + b.line);
                var failed = new BreakpointEventBody();
                failed.reason = "changed";
                failed.breakpoint = b;
                b.verified = false;
                b.message = b.source.name + ":" + b.line + " could not be found or had no code on it";
                client.breakpoint(failed);
                return;
            }
            LOG.info("Enable breakpoint at " + b.source.path + ":" + b.line);
            var ok = new BreakpointEventBody();
            ok.reason = "changed";
            ok.breakpoint = b;
            b.verified = true;
            b.message = null;
            client.breakpoint(ok);
        } catch (AbsentInformationException __) {
            LOG.info("Absent information at " + b.source.path + ":" + b.line);
            var failed = new BreakpointEventBody();
            failed.reason = "changed";
            failed.breakpoint = b;
            b.verified = false;
            b.message = b.source.name + ":" + b.line + " could not be found or had no code on it";
            client.breakpoint(failed);
        }
    }

    private String relativePath(ReferenceType type) {
        try {
            for (var path : type.sourcePaths(vm.getDefaultStratum())) {
                return path;
            }
            return "";
        } catch (AbsentInformationException __) {
            return "";
        }
    }

    @Override
    public void disconnect(DisconnectArguments req) {
        try {
            vm.dispose();
        } catch (VMDisconnectedException __) {
            LOG.warning("VM has already terminated");
        }
        vm = null;
    }

    @Override
    public void terminate(TerminateArguments req) {
        vm.exit(1);
    }

    @Override
    public void continue_(ContinueArguments req) {
        vm.resume();
    }

    @Override
    public void next(NextArguments req) {
        var thread = findThread(req.threadId);
        if (thread == null) {
            LOG.warning("No thread with id " + req.threadId);
            return;
        }
        LOG.info("Send StepRequest(STEP_LINE, STEP_OVER) to VM and resume");
        var step = vm.eventRequestManager().createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_OVER);
        step.addCountFilter(1);
        step.enable();
        vm.resume();
    }

    @Override
    public void stepIn(StepInArguments req) {
        var thread = findThread(req.threadId);
        if (thread == null) {
            LOG.warning("No thread with id " + req.threadId);
            return;
        }
        LOG.info("Send StepRequest(STEP_LINE, STEP_INTO) to VM and resume");
        var step = vm.eventRequestManager().createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
        step.addCountFilter(1);
        step.enable();
        vm.resume();
    }

    @Override
    public void stepOut(StepOutArguments req) {
        var thread = findThread(req.threadId);
        if (thread == null) {
            LOG.warning("No thread with id " + req.threadId);
            return;
        }
        LOG.info("Send StepRequest(STEP_LINE, STEP_OUT) to VM and resume");
        var step = vm.eventRequestManager().createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_OUT);
        step.addCountFilter(1);
        step.enable();
        vm.resume();
    }

    @Override
    public ThreadsResponseBody threads() {
        var threads = new ThreadsResponseBody();
        threads.threads = asThreads(vm.allThreads());
        return threads;
    }

    private org.javacs.debug.proto.Thread[] asThreads(List<ThreadReference> ts) {
        var result = new org.javacs.debug.proto.Thread[ts.size()];
        for (var i = 0; i < ts.size(); i++) {
            result[i] = asThread(ts.get(i));
        }
        return result;
    }

    private org.javacs.debug.proto.Thread asThread(ThreadReference t) {
        var thread = new org.javacs.debug.proto.Thread();
        thread.id = t.uniqueID();
        thread.name = t.name();
        return thread;
    }

    private ThreadReference findThread(long threadId) {
        for (var thread : vm.allThreads()) {
            if (thread.uniqueID() == threadId) {
                return thread;
            }
        }
        return null;
    }

    @Override
    public StackTraceResponseBody stackTrace(StackTraceArguments req) {
        try {
            for (var t : vm.allThreads()) {
                if (t.uniqueID() == req.threadId) {
                    var length = t.frameCount() - req.startFrame;
                    if (req.levels != null && req.levels < length) {
                        length = req.levels;
                    }
                    var resp = new StackTraceResponseBody();
                    resp.stackFrames = asStackFrames(t.frames(req.startFrame, length));
                    resp.totalFrames = t.frameCount();
                    return resp;
                }
            }
            throw new RuntimeException("Couldn't find thread " + req.threadId);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }

    private org.javacs.debug.proto.StackFrame[] asStackFrames(List<com.sun.jdi.StackFrame> fs) {
        var result = new org.javacs.debug.proto.StackFrame[fs.size()];
        for (var i = 0; i < fs.size(); i++) {
            result[i] = asStackFrame(fs.get(i));
        }
        return result;
    }

    private org.javacs.debug.proto.StackFrame asStackFrame(com.sun.jdi.StackFrame f) {
        var frame = new org.javacs.debug.proto.StackFrame();
        frame.id = uniqueFrameId(f);
        frame.name = f.location().method().name();
        frame.source = asSource(f.location());
        frame.line = f.location().lineNumber();
        return frame;
    }

    private Source asSource(Location l) {
        try {
            var path = findSource(l);
            var src = new Source();
            src.name = l.sourceName();
            src.path = Objects.toString(path, null);
            return src;
        } catch (AbsentInformationException __) {
            var src = new Source();
            src.path = relativePath(l.declaringType());
            src.name = l.declaringType().name();
            src.presentationHint = "deemphasize";
            return src;
        }
    }

    private static final Set<String> warnedCouldNotFind = new HashSet<>();

    private Path findSource(Location l) throws AbsentInformationException {
        var relative = l.sourcePath();
        for (var root : sourceRoots) {
            var absolute = root.resolve(relative);
            if (Files.exists(absolute)) {
                return absolute;
            }
        }
        if (!warnedCouldNotFind.contains(relative)) {
            LOG.warning("Could not find " + relative);
            warnedCouldNotFind.add(relative);
        }
        return null;
    }

    /** Debug adapter protocol doesn't seem to like frame 0 */
    private static final int FRAME_OFFSET = 100;

    private long uniqueFrameId(com.sun.jdi.StackFrame f) {
        try {
            long count = FRAME_OFFSET;
            for (var thread : f.virtualMachine().allThreads()) {
                if (thread.equals(f.thread())) {
                    for (var frame : thread.frames()) {
                        if (frame.equals(f)) {
                            return count;
                        } else {
                            count++;
                        }
                    }
                } else {
                    count += thread.frameCount();
                }
            }
            return count;
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }

    private com.sun.jdi.StackFrame findFrame(long id) {
        try {
            long count = FRAME_OFFSET;
            for (var thread : vm.allThreads()) {
                if (id < count + thread.frameCount()) {
                    var offset = (int) (id - count);
                    return thread.frame(offset);
                } else {
                    count += thread.frameCount();
                }
            }
            throw new RuntimeException("Couldn't find frame " + id);
        } catch (IncompatibleThreadStateException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ScopesResponseBody scopes(ScopesArguments req) {
        var resp = new ScopesResponseBody();
        var locals = new Scope();
        locals.name = "Locals";
        locals.presentationHint = "locals";
        locals.variablesReference = req.frameId * 2;
        var arguments = new Scope();
        arguments.name = "Arguments";
        arguments.presentationHint = "arguments";
        arguments.variablesReference = req.frameId * 2 + 1;
        resp.scopes = new Scope[] {locals, arguments};
        return resp;
    }

    @Override
    public VariablesResponseBody variables(VariablesArguments req) {
        var frameId = req.variablesReference / 2;
        var scopeId = (int) (req.variablesReference % 2);
        var argumentScope = scopeId == 1;
        var frame = findFrame(frameId);
        List<LocalVariable> visible;
        try {
            visible = frame.visibleVariables();
        } catch (AbsentInformationException __) {
            LOG.warning(String.format("No visible variable information in %s", frame.location()));
            return new VariablesResponseBody();
        }
        var values = frame.getValues(visible);
        var thread = frame.thread();
        var variables = new ArrayList<Variable>();
        for (var v : visible) {
            if (v.isArgument() != argumentScope) continue;
            var w = new Variable();
            w.name = v.name();
            w.value = print(values.get(v), thread);
            w.type = v.typeName();
            // TODO set variablesReference and allow inspecting structure of collections and POJOs
            // TODO set variablePresentationHint
            variables.add(w);
        }
        var resp = new VariablesResponseBody();
        resp.variables = variables.toArray(Variable[]::new);
        return resp;
    }

    private String print(Value value, ThreadReference t) {
        if (value == null) {
            return "null";
        } else if (value instanceof ObjectReference) {
            return printObject((ObjectReference) value, t);
        } else {
            return value.toString();
        }
    }

    private String printObject(ObjectReference object, ThreadReference t) {
        var type = object.referenceType();
        for (var method : type.methodsByName("toString", "()Ljava/lang/String;")) {
            try {
                var string = (StringReference) object.invokeMethod(t, method, List.of(), 0);
                return string.value();
            } catch (InvocationException e) {
                return String.format("toString() threw %s", e.exception().type().name());
            } catch (InvalidTypeException | ClassNotLoadedException | IncompatibleThreadStateException e) {
                throw new RuntimeException(e);
            }
        }
        return object.toString();
    }

    @Override
    public EvaluateResponseBody evaluate(EvaluateArguments req) {
        throw new UnsupportedOperationException();
    }

    /** Determines if a given type might be provided by a source file. */
    private static boolean mayProvideType(String absolutePath, ReferenceType type) {
        var lastSlashPos = absolutePath.lastIndexOf('/');
        if (lastSlashPos < 0) {
            return true;
        }
        var absoluteDir = absolutePath.substring(0, lastSlashPos);

        var fullName = type.name();
        var lastDotPos = fullName.lastIndexOf('.');
        if (lastDotPos < 0) {
            return true;
        }
        var packageName = fullName.substring(0, lastDotPos);

        // If a type belongs to the package "x.y.z", its source directory should end with "x/y/z".
        // Unfortunately it's difficult to consider class names because of anonymous classes and
        // non-public classes.
        return absoluteDir.endsWith(packageName.replace('.', '/'));
    }

    private static final Logger LOG = Logger.getLogger("debug");
}
