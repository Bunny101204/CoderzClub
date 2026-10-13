import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const srcRoot = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.dirname(srcRoot);

function sourceFiles(dir) {
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...sourceFiles(full));
    } else if (/\.(js|jsx)$/.test(entry.name) && !entry.name.endsWith(".test.js")) {
      files.push(full);
    }
  }
  return files;
}

function stripComments(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, "").replace(/^\s*\/\/.*$/gm, "");
}

test("production problem list uses the configured API client", () => {
  const home = fs.readFileSync(path.join(srcRoot, "Components/HomePage.jsx"), "utf8");
  assert.match(home, /api\.problems\.getAll\(params\)/);
  assert.match(home, /page: currentPage/);
  assert.match(home, /size: itemsPerPage/);
  assert.match(home, /params\.difficulty = difficulty/);
  assert.match(home, /params\.tags = topic/);
  assert.match(home, /params\.search = search\.trim\(\)/);
  assert.doesNotMatch(home, /fetch\(`\/api\/problems/);
});

test("no application request calls a relative /api URL directly", () => {
  const violations = [];
  for (const file of sourceFiles(srcRoot)) {
    const code = stripComments(fs.readFileSync(file, "utf8"));
    for (const match of code.matchAll(/\bfetch\s*\(\s*([^)\n]+)/g)) {
      const arg = match[1].trim();
      const allowed = arg.startsWith("apiUrl(") || arg.startsWith("draftRequestUrl(");
      if (!allowed) violations.push(`${path.relative(repoRoot, file)}: fetch(${arg})`);
    }
    for (const match of code.matchAll(/\baxios\.(get|post|put|patch|delete)\s*\(/g)) {
      violations.push(`${path.relative(repoRoot, file)}: axios.${match[1]}(`);
    }
    for (const match of code.matchAll(/\bnew EventSource\s*\(\s*([^)\n]+)/g)) {
      if (!match[1].trim().startsWith("apiUrl(")) {
        violations.push(`${path.relative(repoRoot, file)}: EventSource(${match[1].trim()})`);
      }
    }
    if (/\bXMLHttpRequest\b|\bsendBeacon\s*\(|\bnew Request\s*\(/.test(code)) {
      violations.push(`${path.relative(repoRoot, file)}: alternate request API`);
    }
  }
  assert.deepEqual(violations, []);
});

test("API client keeps bearer auth and does not patch global fetch", () => {
  const client = fs.readFileSync(path.join(srcRoot, "apiClient.js"), "utf8");
  assert.match(client, /config\.headers\.Authorization = `Bearer \$\{token\}`/);
  assert.match(client, /apiFetch\(path, init\)/);
  assert.match(client, /fetch\(apiUrl\(path\), init\)/);
  assert.doesNotMatch(client, /globalThis\.fetch\s*=/);
  assert.doesNotMatch(client, /axios\.defaults\.baseURL/);
  assert.match(client, /\/api\/submission-jobs\/\$\{jobId\}/);
  assert.match(client, /\/api\/problems\/\$\{encodeURIComponent\(problemId\)\}\/drafts\//);
  assert.match(client, /\/api\/contests\/\$\{contestId\}\/problems\/\$\{problemId\}\/submission-jobs/);
  assert.match(client, /\/api\/admin\/batches/);
});

test("local Vite proxy remains the development /api route", () => {
  const vite = fs.readFileSync(path.join(repoRoot, "vite.config.js"), "utf8");
  assert.match(vite, /proxy:\s*\{[\s\S]*['"]\/api['"]/);
  assert.match(vite, /target:\s*'http:\/\/localhost:8080'/);
  assert.match(vite, /Dev server only/);
});
