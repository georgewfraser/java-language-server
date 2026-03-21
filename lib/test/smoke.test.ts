'use strict';

import * as assert from 'assert';
import * as Path from 'path';
import * as vscode from 'vscode';

suite('Extension Smoke', () => {
    test('activates, resolves definitions, and publishes diagnostics for Java files', async () => {
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

        const definitionUri = vscode.Uri.file(Path.join(folders[0].uri.fsPath, 'GotoDefinition.java'));
        const definitionDocument = await vscode.workspace.openTextDocument(definitionUri);
        const definitionEditor = await vscode.window.showTextDocument(definitionDocument);

        const definitionLine = definitionDocument.lineAt(2).text;
        const definitionOffset = definitionLine.indexOf('goToHere');
        assert.ok(definitionOffset >= 0, 'Expected goToHere call in GotoDefinition.java');

        const definitionPosition = new vscode.Position(2, definitionOffset + 2);
        definitionEditor.selection = new vscode.Selection(definitionPosition, definitionPosition);
        definitionEditor.revealRange(new vscode.Range(definitionPosition, definitionPosition));

        const definitions = await waitForDefinitions(definitionUri, definitionPosition, 30000);
        const target = definitionTarget(definitions[0]);
        const targetDocument = target.uri.toString() === definitionDocument.uri.toString()
            ? definitionDocument
            : await vscode.workspace.openTextDocument(target.uri);
        const targetLine = targetDocument.lineAt(target.range.start.line).text;
        assert.ok(
            targetLine.includes('void goToHere()'),
            `Expected definition for goToHere, got line: ${targetLine || '<none>'}`
        );
    });
});

async function waitForDefinitions(
    uri: vscode.Uri,
    position: vscode.Position,
    timeoutMs: number
): Promise<Array<vscode.Location | vscode.LocationLink>> {
    const start = Date.now();

    while (Date.now() - start < timeoutMs) {
        const definitions = await withTimeout(
            vscode.commands.executeCommand<Array<vscode.Location | vscode.LocationLink>>(
                'vscode.executeDefinitionProvider',
                uri,
                position
            ),
            5000,
            'Definition request timed out'
        );
        if ((definitions?.length ?? 0) > 0) {
            return definitions ?? [];
        }
        await delay(250);
    }

    const definitions = await withTimeout(
        vscode.commands.executeCommand<Array<vscode.Location | vscode.LocationLink>>(
            'vscode.executeDefinitionProvider',
            uri,
            position
        ),
        5000,
        'Definition request timed out'
    );
    return definitions ?? [];
}

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

function definitionTarget(definition: vscode.Location | vscode.LocationLink): { uri: vscode.Uri; range: vscode.Range } {
    if ('targetUri' in definition) {
        return {
            uri: definition.targetUri,
            range: definition.targetRange
        };
    }

    return {
        uri: definition.uri,
        range: definition.range
    };
}

function delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function withTimeout<T>(promise: Thenable<T>, timeoutMs: number, message: string): Promise<T> {
    let timeoutId: NodeJS.Timeout | undefined;
    try {
        return await Promise.race([
            promise,
            new Promise<T>((_, reject) => {
                timeoutId = setTimeout(() => reject(new Error(message)), timeoutMs);
            })
        ]);
    } finally {
        if (timeoutId) clearTimeout(timeoutId);
    }
}
