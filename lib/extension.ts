'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as Path from "path";
import * as FS from "fs";
import {window, workspace, ExtensionContext, commands, tasks, Task, TaskExecution, ShellExecution, Uri, TaskDefinition, languages, IndentAction, Progress, ProgressLocation, debug, DebugConfiguration, Range, Position, TextDocument, TextDocumentContentProvider, CancellationToken, ProviderResult, ConfigurationChangeEvent} from 'vscode';
import {LanguageClient, LanguageClientOptions, ServerOptions, NotificationType} from "vscode-languageclient";
import {loadStyles, decoration} from './textMate';
import * as AdmZip from 'adm-zip';

// If we want to profile using VisualVM, we have to run the language server using regular java, not jlink
const visualVm = false;

/** Called when extension is activated */
export async function activate(context: ExtensionContext) {
    console.log('Activating Java');

    // Teach VSCode to open JAR files
    workspace.registerTextDocumentContentProvider('jar', new JarFileSystemProvider());
    
    // Options to control the language client
    let clientOptions: LanguageClientOptions = {
        // Register the server for java documents
        documentSelector: [{scheme: 'file', language: 'java'}],
        synchronize: {
            // Synchronize the setting section 'java' to the server
            // NOTE: this currently doesn't do anything
            configurationSection: 'java',
            // Notify the server about file changes to 'javaconfig.json' files contain in the workspace
            fileEvents: [
                workspace.createFileSystemWatcher('**/javaconfig.json'),
                workspace.createFileSystemWatcher('**/pom.xml'),
                workspace.createFileSystemWatcher('**/WORKSPACE'),
                workspace.createFileSystemWatcher('**/BUILD'),
                workspace.createFileSystemWatcher('**/*.java')
            ]
        },
        outputChannelName: 'Java',
        revealOutputChannelOn: 4 // never
    }

    let launcherRelativePath = platformSpecificLangServer();
    let launcherPath = [context.extensionPath].concat(launcherRelativePath);
    let launcher = Path.resolve(...launcherPath);
    
    // Start the child java process
    let serverOptions: ServerOptions = {
        command: launcher,
        args: [],
        options: { cwd: context.extensionPath }
    }

    if (visualVm) {
        serverOptions = visualVmConfig(context);
    }

    enableJavadocSymbols();

    // Create the language client and start the client.
    let client = new LanguageClient('java', 'Java Language Server', serverOptions, clientOptions);
    let disposable = client.start();

    // Push the disposable to the context's subscriptions so that the 
    // client can be deactivated on extension deactivation
    context.subscriptions.push(disposable);

    // Register test commands
    commands.registerCommand('java.command.test.run', runTest);
    commands.registerCommand('java.command.test.debug', debugTest);
    commands.registerCommand('java.command.findReferences', runFindReferences);

	// When the language client activates, register a progress-listener
    client.onReady().then(() => createProgressListeners(client));

    // Apply semantic colors using custom notification
    function asRange(r: RangeLike) {
        return new Range(asPosition(r.start), asPosition(r.end));
    }
    function asPosition(p: PositionLike) {
        return new Position(p.line, p.character);
    }
    const statics = window.createTextEditorDecorationType({
        fontStyle: 'italic'
    });
    const colors = new Map<string, SemanticColors>();
    function cacheSemanticColors(event: SemanticColors) {
        colors.set(event.uri, event);
        applySemanticColors();
    }
    function applySemanticColors() {
        for (const editor of window.visibleTextEditors) {
            if (editor.document.languageId != 'java') continue;
            const c = colors.get(editor.document.uri.toString());
            if (c == null) {
                console.warn('No semantic colors for ' + editor.document.uri)
                continue;
            }
            function decorate(scope: string, ranges: RangeLike[]) {
                const d = decoration(scope);
                if (d == null) {
                    console.warn(scope + ' is not defined in the current theme');
                    return;
                }
                editor.setDecorations(d, ranges.map(asRange));
            }
            decorate('variable', c.fields);
            editor.setDecorations(statics, c.statics.map(asRange));
        }
    }
    function forgetSemanticColors(doc: TextDocument) {
        colors.delete(doc.uri.toString());
    }
	// Load active color theme
	async function onChangeConfiguration(event: ConfigurationChangeEvent) {
        let colorizationNeedsReload: boolean = event.affectsConfiguration('workbench.colorTheme')
			|| event.affectsConfiguration('editor.tokenColorCustomizations')
		if (colorizationNeedsReload) {
			await loadStyles()
			applySemanticColors()
		}
	}
    client.onReady().then(() => {
        client.onNotification(new NotificationType('java/colors'), cacheSemanticColors);
        context.subscriptions.push(window.onDidChangeVisibleTextEditors(applySemanticColors));
        context.subscriptions.push(workspace.onDidCloseTextDocument(forgetSemanticColors));
        context.subscriptions.push(workspace.onDidChangeConfiguration(onChangeConfiguration))
    });
    await loadStyles();
    applySemanticColors();
}

// Allows VSCode to open files like jar:file:///path/to/dep.jar!/com/foo/Thing.java
class JarFileSystemProvider implements TextDocumentContentProvider {
    private cache = new Map<string, AdmZip>();
    provideTextDocumentContent(uri: Uri, _token: CancellationToken): ProviderResult<string> {
        const {zip, file} = this.splitZipUri(uri);
        return this.readZip(zip, file);
    }
    private splitZipUri(uri: Uri): {zip: string, file: string} {
        const path = uri.fsPath.substring("file://".length);
        const [zip, file] = path.split('!/');
        return {zip, file};
    }
    private readZip(zip: string, file: string): Promise<string> {
        return new Promise((resolve, reject) => {
            try {
                if (!this.cache.has(zip)) {
                    this.cache.set(zip, new AdmZip(zip));
                }
                this.cache.get(zip).readAsTextAsync(file, resolve);
            } catch (error) {
                reject(error);
            }
        });
    }
}

// this method is called when your extension is deactivated
export function deactivate() {
}

function runFindReferences(uri: string, lineNumber: number, column: number) {
    // LSP is 0-based but VSCode is 1-based
    return commands.executeCommand('editor.action.findReferences', Uri.parse(uri), {lineNumber: lineNumber+1, column: column+1});
}

interface JavaTestTask extends TaskDefinition {
    className: string
    methodName: string
}

function runTest(sourceUri: string, className: string, methodName: string|null): Thenable<TaskExecution> {
    let file = Uri.parse(sourceUri).fsPath;
    file = Path.relative(workspace.rootPath, file);
	let test: JavaTestTask = {
		type: 'java.task.test',
        className: className,
        methodName: methodName,
    }
    let shell = testShell(file, className, methodName);
    if (shell == null) return null;
	let workspaceFolder = workspace.getWorkspaceFolder(Uri.parse(sourceUri));
	let testTask = new Task(test, workspaceFolder, 'Java Test', 'Java Language Server', shell);
	return tasks.executeTask(testTask)
}

function testShell(file: string, className: string, methodName: string|null) {
    let config = workspace.getConfiguration('java')
    // Run method or class
    if (methodName != null) {
        let command = config.get('testMethod') as string[]
        if (command.length == 0) {
            window.showErrorMessage('Set "java.testMethod" in .vscode/settings.json');
            return null;
        } else {
            return templateCommand(command, file, className, methodName)
        }
    } else {
        let command = config.get('testClass') as string[]
        if (command.length == 0) {
            window.showErrorMessage('Set "java.testClass" in .vscode/settings.json');
            return null;
        } else {
            return templateCommand(command, file, className, methodName)
        }
    }
}

async function debugTest(sourceUri: string, className: string, methodName: string, sourceRoots: string[]): Promise<boolean> {
    let file = Uri.parse(sourceUri).fsPath;
    file = Path.relative(workspace.rootPath, file);
    // Run the test in its own shell
	let test: JavaTestTask = {
		type: 'java.task.test',
        className: className,
        methodName: methodName,
    }
    let shell = debugTestShell(file, className, methodName);
    if (shell == null) return null;
	let workspaceFolder = workspace.getWorkspaceFolder(Uri.parse(sourceUri));
	let testTask = new Task(test, workspaceFolder, 'Java Test', 'Java Language Server', shell);
    await tasks.executeTask(testTask);
    // Attach to the running test
	let attach: DebugConfiguration = {
        name: 'Java Debug',
        type: 'java',
        request: 'attach',
        port: 5005,
        sourceRoots: sourceRoots,
    }
    console.log('Debug', JSON.stringify(attach));
    return debug.startDebugging(workspaceFolder, attach);
}

function debugTestShell(file: string, className: string, methodName: string) {
    let config = workspace.getConfiguration('java')
    let command = config.get('debugTestMethod') as string[]
    if (command.length == 0) {
        window.showErrorMessage('Set "java.debugTestMethod" in .vscode/settings.json');
        return null;
    } else {
        return templateCommand(command, file, className, methodName)
    }
}

function templateCommand(command: string[], file: string, className: string, methodName: string) {
    // Replace template parameters
    var replaced = []
    for (var i = 0; i < command.length; i++) {
        let c = command[i]
        c = c.replace('${file}', file)
        c = c.replace('${class}', className)
        c = c.replace('${method}', methodName)
        replaced[i] = c
    }
    // Populate env
    let env = {...process.env} as {[key: string]: string};
    return new ShellExecution(replaced[0], replaced.slice(1), {env})
}

interface ProgressMessage {
    message: string 
    increment: number
}

function createProgressListeners(client: LanguageClient) {
	// Create a "checking files" progress indicator
	let progressListener = new class {
		progress: Progress<{message: string, increment?: number}>
		resolve: (nothing: {}) => void
		
		startProgress(message: string) {
            if (this.progress != null)
                this.endProgress();

            window.withProgress({title: message, location: ProgressLocation.Notification}, progress => new Promise((resolve, _reject) => {
                this.progress = progress;
                this.resolve = resolve;
            }));
		}
		
		reportProgress(message: string, increment: number) {
            if (increment == -1)
                this.progress.report({message});
            else 
                this.progress.report({message, increment})
		}

		endProgress() {
            if (this.progress != null) {
                this.resolve({});
                this.progress = null;
                this.resolve = null;
            }
		}
	}
	// Use custom notifications to drive progressListener
	client.onNotification(new NotificationType('java/startProgress'), (event: ProgressMessage) => {
		progressListener.startProgress(event.message);
	});
	client.onNotification(new NotificationType('java/reportProgress'), (event: ProgressMessage) => {
		progressListener.reportProgress(event.message, event.increment);
	});
	client.onNotification(new NotificationType('java/endProgress'), () => {
		progressListener.endProgress();
	});
};

interface SemanticColors {
    uri: string;
    fields: RangeLike[];
    statics: RangeLike[];
}

interface RangeLike {
    start: PositionLike;
    end: PositionLike;
}

interface PositionLike {
    line: number;
    character: number;
}

function platformSpecificLangServer(): string[] {
	switch (process.platform) {
		case 'win32':
            return ['dist', 'lang_server_windows.sh'];

        case 'darwin':
            return ['dist', 'lang_server_mac.sh'];

        case 'linux':
            return ['dist', 'lang_server_linux.sh'];
	}

	throw `unsupported platform: ${process.platform}`;
}

// Alternative server options if you want to use visualvm
function visualVmConfig(context: ExtensionContext): ServerOptions {
    let javaExecutablePath = findJavaExecutable('java');
    
    if (javaExecutablePath == null) {
        window.showErrorMessage("Couldn't locate java in $JAVA_HOME or $PATH");
        
        throw "Gave up";
    }
    const jars = [
        'gson-2.8.9.jar',
        'java-language-server.jar',
        'protobuf-java-3.19.3.jar',
    ];
    const classpath = jars.map(jar => Path.resolve(context.extensionPath, "dist", "classpath", jar)).join(':');
    let args = [
        '-cp', classpath, 
        '-Xverify:none', // helps VisualVM avoid 'error 62'
        '-Xdebug',
        // '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005',
        'org.javacs.Main',
        // Exports, needed at compile and runtime for access
        "--add-exports", "jdk.compiler/com.sun.tools.javac.api=javacs",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.code=javacs",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.comp=javacs",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.main=javacs",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=javacs",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.model=javacs",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.util=javacs",
        // Opens, needed at runtime for reflection
        "--add-opens", "jdk.compiler/com.sun.tools.javac.api=javacs",
    ];
    
    console.log(javaExecutablePath + ' ' + args.join(' '));
    
    // Start the child java process
    return {
        command: javaExecutablePath,
        args: args,
        options: { cwd: context.extensionPath }
    }
}

function findJavaExecutable(binname: string) {
	binname = correctBinname(binname);

	// First search java.home setting
    let userJavaHome = workspace.getConfiguration('java').get('home') as string;

	if (userJavaHome != null) {
        console.log('Looking for java in settings java.home ' + userJavaHome + '...');

        let candidate = findJavaExecutableInJavaHome(userJavaHome, binname);

        if (candidate != null)
            return candidate;
	}

	// Then search each JAVA_HOME
    let envJavaHome = process.env['JAVA_HOME'];

	if (envJavaHome) {
        console.log('Looking for java in environment variable JAVA_HOME ' + envJavaHome + '...');

        let candidate = findJavaExecutableInJavaHome(envJavaHome, binname);

        if (candidate != null)
            return candidate;
	}

	// Then search PATH parts
	if (process.env['PATH']) {
        console.log('Looking for java in PATH');
        
		let pathparts = process.env['PATH'].split(Path.delimiter);
		for (let i = 0; i < pathparts.length; i++) {
			let binpath = Path.join(pathparts[i], binname);
			if (FS.existsSync(binpath)) {
				return binpath;
			}
		}
	}
    
	// Else return the binary name directly (this will likely always fail downstream) 
	return null;
}

function correctBinname(binname: string) {
	if (process.platform === 'win32')
		return binname + '.exe';
	else
		return binname;
}

function findJavaExecutableInJavaHome(javaHome: string, binname: string) {
    let workspaces = javaHome.split(Path.delimiter);

    for (let i = 0; i < workspaces.length; i++) {
        let binpath = Path.join(workspaces[i], 'bin', binname);

        if (FS.existsSync(binpath)) 
            return binpath;
    }

    return null;
}

function enableJavadocSymbols() {
	// Let's enable Javadoc symbols autocompletion, shamelessly copied from MIT licensed code at
	// https://github.com/Microsoft/vscode/blob/9d611d4dfd5a4a101b5201b8c9e21af97f06e7a7/extensions/typescript/src/typescriptMain.ts#L186
	languages.setLanguageConfiguration('java', {
		indentationRules: {
			// ^(.*\*/)?\s*\}.*$
			decreaseIndentPattern: /^(.*\*\/)?\s*\}.*$/,
			// ^.*\{[^}"']*$
			increaseIndentPattern: /^.*\{[^}"']*$/
		},
		wordPattern: /(-?\d*\.\d\w*)|([^\`\~\!\@\#\%\^\&\*\(\)\-\=\+\[\{\]\}\\\|\;\:\'\"\,\.\<\>\/\?\s]+)/g,
		onEnterRules: [
			{
				// e.g. /** | */
				beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
				afterText: /^\s*\*\/$/,
				action: { indentAction: IndentAction.IndentOutdent, appendText: ' * ' }
			},
			{
				// e.g. /** ...|
				beforeText: /^\s*\/\*\*(?!\/)([^\*]|\*(?!\/))*$/,
				action: { indentAction: IndentAction.None, appendText: ' * ' }
			},
			{
				// e.g.  * ...|
				beforeText: /^(\t|(\ \ ))*\ \*(\ ([^\*]|\*(?!\/))*)?$/,
				action: { indentAction: IndentAction.None, appendText: '* ' }
			},
			{
				// e.g.  */|
				beforeText: /^(\t|(\ \ ))*\ \*\/\s*$/,
				action: { indentAction: IndentAction.None, removeText: 1 }
			},
			{
				// e.g.  *-----*/|
				beforeText: /^(\t|(\ \ ))*\ \*[^/]*\*\/\s*$/,
				action: { indentAction: IndentAction.None, removeText: 1 }
			}
		]
	});
}
