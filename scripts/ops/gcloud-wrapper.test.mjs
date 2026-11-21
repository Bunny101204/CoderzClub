import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { spawn } from 'node:child_process';
import { test } from 'node:test';

const root = path.resolve(import.meta.dirname, '../..');
const modulePath = path.join(root, 'scripts', 'ops', 'gcloud-wrapper.psm1');

function runPowerShell(command, env) {
  return new Promise((resolve, reject) => {
    const child = spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command], { env });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => { stdout += chunk; });
    child.stderr.on('data', (chunk) => { stderr += chunk; });
    child.on('error', reject);
    child.on('close', (code) => resolve({ code, stdout, stderr }));
  });
}

test('gcloud wrapper forwards real arguments to the Windows executable', async () => {
  const temp = await mkdtemp(path.join(os.tmpdir(), 'coderzclub-gcloud-'));
  const log = path.join(temp, 'args.log');
  const mock = path.join(temp, 'gcloud.cmd');
  await writeFile(mock, '@echo off\r\n> "%MOCK_GCLOUD_LOG%" echo %*\r\nexit /b 0\r\n');

  try {
    const env = {
      ...process.env,
      MOCK_GCLOUD_LOG: log,
      PATH: `${temp}${path.delimiter}${process.env.PATH ?? ''}`,
    };
    const command = [
      `$env:OS = 'Windows_NT'`,
      `Import-Module '${modulePath.replaceAll("'", "''")}' -Force`,
      `Invoke-Gcloud @('compute','instances','list','--project=coderzclub-load-test','--format=json') | Out-Null`,
    ].join('; ');
    const result = await runPowerShell(command, env);
    assert.equal(result.code, 0, `${result.stdout}\n${result.stderr}`);
    assert.equal((await readFile(log, 'utf8')).trim(), 'compute instances list --project=coderzclub-load-test --format=json');
  } finally {
    await rm(temp, { recursive: true, force: true });
  }
});
