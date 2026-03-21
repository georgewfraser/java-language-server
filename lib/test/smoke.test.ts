'use strict';

import * as assert from 'assert';
import * as Path from 'path';
import * as vscode from 'vscode';

suite('Extension Smoke', () => {
    test('activates and publishes diagnostics for Java files', async () => {
        const extension = vscode.extensions.getExtension('georgewfraser.vscode-javac');
        assert.ok(extension, 'Extension georgewfraser.vscode-javac was not found');

        await extension.activate();

        const commands = await vscode.commands.getCommands(true);
        assert.ok(commands.includes('java.command.findReferences'), 'Expected Java commands to be registered');

        const folders = vscode.workspace.workspaceFolders;
        assert.ok(folders && folders.length > 0, 'Expected the smoke-test workspace to be open');

        const uri = vscode.Uri.file(Path.join(folders[0].uri.fsPath, 'HelloError.java'));
        const document = await vscode.workspace.openTextDocument(uri);
        await vscode.window.showTextDocument(document);

        const diagnostics = await waitForDiagnostics(uri, 90000);
        const messages = diagnostics.map(d => d.message).join(' | ');

        assert.ok(
            diagnostics.some(d => d.severity === vscode.DiagnosticSeverity.Error),
            `Expected an error diagnostic for HelloError.java, got: ${messages || '<none>'}`
        );
    });
});

async function waitForDiagnostics(uri: vscode.Uri, timeoutMs: number): Promise<vscode.Diagnostic[]> {
    const start = Date.now();

    while (Date.now() - start < timeoutMs) {
        const diagnostics = vscode.languages.getDiagnostics(uri);
        if (diagnostics.some(d => d.severity === vscode.DiagnosticSeverity.Error)) {
            return diagnostics;
        }
        await delay(250);
    }

    return vscode.languages.getDiagnostics(uri);
}

function delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}
