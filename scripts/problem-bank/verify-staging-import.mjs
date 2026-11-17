#!/usr/bin/env node
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const creds = JSON.parse(readFileSync(resolve(root, ".local/problem-bank/pb-staging-admin.json"), "utf8"));
const manifest = JSON.parse(readFileSync(resolve(root, ".local/problem-bank/manifests/phase1.json"), "utf8"));
const apiBase = process.env.PROBLEM_BANK_API_BASE || creds.apiBase || "http://127.0.0.1:18090";

async function api(method, path, { token, data } = {}) {
  const res = await fetch(`${apiBase}${path}`, {
    method,
    headers: {
      Accept: "application/json",
      ...(data ? { "Content-Type": "application/json" } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: data ? JSON.stringify(data) : undefined,
  });
  const text = await res.text();
  let body = text;
  try { body = text ? JSON.parse(text) : null; } catch { /* keep text */ }
  return { status: res.status, body };
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function waitJob(token, jobId, timeoutMs = 120000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const res = await api("GET", `/api/submission-jobs/${jobId}`, { token });
    const status = res.body?.status || res.body?.jobStatus || res.body?.state;
    const verdict = res.body?.verdict
      || res.body?.overallVerdict
      || (typeof res.body?.result === "string" ? res.body.result : res.body?.result?.verdict);
    if (["COMPLETED", "FAILED", "DONE", "FINISHED"].includes(String(status || "").toUpperCase())
      || ["ACCEPTED", "WRONG_ANSWER", "WA", "AC", "RUNTIME_ERROR", "COMPILATION_ERROR", "TIME_LIMIT_EXCEEDED", "INTERNAL_ERROR"].includes(String(verdict || "").toUpperCase())) {
      return res.body;
    }
    if (res.body?.job && (res.body.job.verdict || res.body.job.status || res.body.job.result)) {
      const st = String(res.body.job.status || "").toUpperCase();
      if (["COMPLETED", "FAILED", "DONE"].includes(st) || res.body.job.verdict || res.body.job.result) return res.body.job;
    }
    await sleep(2000);
  }
  throw new Error(`Timeout waiting for job ${jobId}`);
}

async function main() {
  const login = await api("POST", "/api/login", { data: { username: creds.username, password: creds.password } });
  if (!login.body?.token) throw new Error("login failed");
  const token = login.body.token;

  const problemIds = Object.values(manifest.problems || {});
  if (problemIds.length !== 34) throw new Error(`expected 34 manifest problems, got ${problemIds.length}`);

  const sample = problemIds.find((p) => p.title === "Array Sum") || problemIds[0];
  const publicView = await api("GET", `/api/problems/${sample.id}`);
  const adminView = await api("GET", `/api/problems/${sample.id}/admin`, { token });

  const publicHasHidden = publicView.body?.hiddenTestCases != null
    && Array.isArray(publicView.body.hiddenTestCases)
    && publicView.body.hiddenTestCases.length > 0;
  const adminHiddenCount = Array.isArray(adminView.body?.hiddenTestCases)
    ? adminView.body.hiddenTestCases.length
    : -1;
  const publicCases = Array.isArray(publicView.body?.publicTestCases) ? publicView.body.publicTestCases.length : 0;

  const bundles = await api("GET", "/api/bundles");
  const bundleList = Array.isArray(bundles.body)
    ? bundles.body
    : bundles.body?.content || bundles.body?.bundles || [];
  const ourBundles = bundleList.filter((b) =>
    ["Arrays — Fundamentals", "Strings", "Two Pointers"].includes(b.name)
  );

  // Student-ish redaction check: unauthenticated GET must not expose hidden cases.
  const report = {
    apiBase,
    problemsInManifest: problemIds.length,
    sampleTitle: sample.title,
    sampleDifficulty: publicView.body?.difficulty || adminView.body?.difficulty,
    publicCases,
    publicLeakedHidden: publicHasHidden,
    adminHiddenCount,
    bundlesFound: ourBundles.map((b) => ({
      name: b.name,
      totalProblems: b.totalProblems ?? b.problemIds?.length,
      difficulty: b.difficulty,
    })),
    judge: {},
  };

  if (publicHasHidden) throw new Error("hidden tests leaked on public GET");
  if (adminHiddenCount < 6) throw new Error(`admin hidden count unexpected: ${adminHiddenCount}`);
  if (ourBundles.length < 3) throw new Error(`expected 3 bundles, found ${ourBundles.length}`);

  // Judge: Python 3 languageId 71
  const acceptedCode = `import sys
data=sys.stdin.read().strip().split()
if not data:
    print(0)
else:
    n=int(data[0]); print(sum(map(int,data[1:1+n])) if n else 0)
`;
  const wrongCode = `print(0)\n`;

  const acceptCreate = await api("POST", "/api/submission-jobs", {
    token,
    data: { problemId: sample.id, languageId: 71, code: acceptedCode },
  });
  const acceptJobId = acceptCreate.body?.id || acceptCreate.body?.jobId || acceptCreate.body?.job?.id;
  if (!acceptJobId) {
    report.judge.acceptCreate = { status: acceptCreate.status, body: acceptCreate.body };
    throw new Error("accept job create failed");
  }
  const acceptResult = await waitJob(token, acceptJobId);
  report.judge.accepted = {
    jobId: acceptJobId,
    status: acceptResult.status,
    verdict: acceptResult.verdict || acceptResult.overallVerdict || acceptResult.result,
    passedTests: acceptResult.progress,
    error: acceptResult.error,
  };

  await sleep(1500);
  const wrongCreate = await api("POST", "/api/submission-jobs", {
    token,
    data: { problemId: sample.id, languageId: 71, code: wrongCode },
  });
  const wrongJobId = wrongCreate.body?.id || wrongCreate.body?.jobId || wrongCreate.body?.job?.id;
  if (!wrongJobId) {
    report.judge.wrongCreate = { status: wrongCreate.status, body: wrongCreate.body };
    throw new Error("wrong job create failed");
  }
  const wrongResult = await waitJob(token, wrongJobId);
  report.judge.wrong = {
    jobId: wrongJobId,
    status: wrongResult.status,
    verdict: wrongResult.verdict || wrongResult.overallVerdict || wrongResult.result,
    passedTests: wrongResult.progress,
    error: wrongResult.error,
  };

  const outDir = resolve(root, ".local/problem-bank/reports");
  mkdirSync(outDir, { recursive: true });
  writeFileSync(resolve(outDir, "phase1-staging-verify.json"), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));

  const acc = String(report.judge.accepted.verdict || "").toUpperCase();
  const wa = String(report.judge.wrong.verdict || "").toUpperCase();
  if (!acc.includes("ACCEPT") && acc !== "AC") {
    // still print report; exit non-zero
    console.error("Accepted submission did not get ACCEPTED");
    process.exit(2);
  }
  if (!(wa.includes("WRONG") || wa === "WA" || wa.includes("FAIL"))) {
    console.error("Wrong submission did not get WRONG_ANSWER-like verdict");
    process.exit(3);
  }
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
