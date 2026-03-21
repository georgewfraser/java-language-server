import { defineConfig } from '@vscode/test-cli';

export default defineConfig({
    files: 'out/lib/test/**/*.test.js',
    workspaceFolder: './src/test/examples/simple-project',
    launchArgs: [
        '--disable-gpu',
        '--skip-welcome',
        '--skip-release-notes',
        '--disable-workspace-trust'
    ],
    mocha: {
        timeout: 120000
    }
});
