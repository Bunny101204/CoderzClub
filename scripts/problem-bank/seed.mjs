import { createRequire } from "node:module";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import process from "node:process";
import { apiJson, loginAdmin } from "./lib/api.mjs";
import { assertApplyAllowed } from "./lib/gates.mjs";
import { contentHash } from "./lib/hash.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(here, "../..");
const require = createRequire(import.meta.url);

function parseArgs(argv) {
  const args = {
    phase: 1,
    apply: false,
    allowProduction: false,
    verifyOnly: false,
    apiBase: process.env.PROBLEM_BANK_API_BASE || "",
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--apply") args.apply = true;
    else if (arg === "--allow-production") args.allowProduction = true;
    else if (arg === "--verify-only") args.verifyOnly = true;
    else if (arg === "--phase") args.phase = Number(argv[++i]);
    else if (arg === "--api-base") args.apiBase = argv[++i];
    else if (arg === "--help") args.help = true;
    else throw new Error(`Unknown argument: ${arg}`);
  }
  return args;
}

function loadCredentials() {
  if (process.env.PROBLEM_BANK_ADMIN_TOKEN) {
    return { token: process.env.PROBLEM_BANK_ADMIN_TOKEN };
  }
  const file = process.env.PROBLEM_BANK_CREDENTIALS_FILE;
  if (!file) return null;
  const raw = JSON.parse(readFileSync(file, "utf8"));
  if (!raw.username || !raw.password) throw new Error("credentials file must include username/password");
  return { username: raw.username, password: raw.password };
}

function publicPayload(problem) {
  return {
    title: problem.title,
    statement: problem.statement,
    difficulty: problem.difficulty,
    category: problem.category,
    tags: problem.tags,
    inputFormat: problem.inputFormat,
    outputFormat: problem.outputFormat,
    constraints: problem.constraints,
    hints: problem.hints,
    exampleInput: problem.exampleInput,
    exampleOutput: problem.exampleOutput,
    exampleExplanation: problem.exampleExplanation,
    points: problem.points,
    estimatedTime: problem.estimatedTime,
    availability: problem.availability,
    executionMode: problem.executionMode,
    testcaseVersion: problem.testcaseVersion,
    publicTestCases: problem.publicTestCases.map(({ input, output, explanation }) => ({
      input,
      output,
      ...(explanation ? { explanation } : {}),
    })),
    hiddenTestCases: problem.hiddenTestCases.map(({ input, output }) => ({ input, output })),
  };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    console.log("Usage: node scripts/problem-bank/seed.mjs --phase 1 [--verify-only] [--dry-run|--apply --api-base URL]");
    process.exit(0);
  }

  const phaseDir = resolve(root, `.local/problem-bank/phase${args.phase}/index.mjs`);
  if (!existsSync(phaseDir)) {
    throw new Error(`Missing private phase bank at ${phaseDir}`);
  }
  const mod = await import(pathToFileURL(phaseDir).href);
  const problems = mod.getPhaseProblems
    ? mod.getPhaseProblems()
    : mod.getPhase1Problems
      ? mod.getPhase1Problems()
      : [];
  const bundles = mod.getPhaseBundles
    ? mod.getPhaseBundles(problems)
    : mod.getPhase1Bundles
      ? mod.getPhase1Bundles(problems)
      : [];

  if (args.verifyOnly || !args.apply) {
    const verifyPath = resolve(root, `.local/problem-bank/verify-phase${args.phase}.mjs`);
    if (existsSync(verifyPath)) {
      const { spawnSync } = await import("node:child_process");
      const result = spawnSync(process.execPath, [verifyPath], { cwd: root, encoding: "utf8", shell: false });
      if (result.stdout) process.stdout.write(result.stdout);
      if (result.stderr) process.stderr.write(result.stderr);
      if (result.status !== 0) process.exit(result.status || 1);
    }
  }

  const gate = assertApplyAllowed({
    apiBase: args.apiBase,
    allowProduction: args.allowProduction,
    apply: args.apply,
  });
  if (!gate.allowed) {
    console.error(gate.reasons.join("; "));
    process.exit(2);
  }

  const report = {
    phase: args.phase,
    mode: args.apply ? "apply" : "dry-run",
    apiBase: args.apply ? gate.apiBase : null,
    production: Boolean(gate.production),
    totals: {
      problems: problems.length,
      easy: problems.filter((p) => p.difficulty === "EASY").length,
      medium: problems.filter((p) => p.difficulty === "MEDIUM").length,
      hard: problems.filter((p) => p.difficulty === "HARD").length,
    },
    planned: problems.map((p) => ({
      slug: p.slug,
      title: p.title,
      difficulty: p.difficulty,
      category: p.category,
      publicCases: p.publicTestCases.length,
      hiddenCases: p.hiddenTestCases.length,
      hash: contentHash(p),
    })),
    bundles: bundles.map((b) => ({ name: b.name, category: b.category, problemCount: b.problemSlugs.length })),
    results: [],
  };

  if (!args.apply) {
    const outDir = resolve(root, ".local/problem-bank/reports");
    mkdirSync(outDir, { recursive: true });
    const outPath = resolve(outDir, `phase${args.phase}-dry-run.json`);
    writeFileSync(outPath, JSON.stringify(report, null, 2));
    console.log(`Dry-run OK: ${problems.length} problems, ${bundles.length} bundles. Report: ${outPath}`);
    console.log("No API writes performed.");
    return;
  }

  if (gate.production) {
    console.warn("WARNING: applying to a production-classified API host with explicit --allow-production.");
  }

  const creds = loadCredentials();
  if (!creds) throw new Error("Set PROBLEM_BANK_ADMIN_TOKEN or PROBLEM_BANK_CREDENTIALS_FILE for --apply");
  const token = creds.token || await loginAdmin(gate.apiBase, creds);

  const manifestPath = resolve(root, `.local/problem-bank/manifests/phase${args.phase}.json`);
  const manifest = existsSync(manifestPath) ? JSON.parse(readFileSync(manifestPath, "utf8")) : { problems: {}, bundles: {} };

  // Existing catalog titles for conflict detection (platform list may paginate; best-effort first pages).
  const existingTitles = new Map();
  for (let page = 0; page < 20; page += 1) {
    const list = await apiJson(gate.apiBase, "GET", `/api/problems?page=${page}&size=50`, {
      token,
      expectedStatuses: [200],
    });
    const rows = Array.isArray(list.body?.content)
      ? list.body.content
      : Array.isArray(list.body?.problems)
        ? list.body.problems
        : Array.isArray(list.body)
          ? list.body
          : [];
    if (!rows.length) break;
    for (const row of rows) {
      if (row?.title && row?.id) existingTitles.set(String(row.title), String(row.id));
    }
    if (rows.length < 50) break;
  }

  const slugToId = {};
  for (const problem of problems) {
    const hash = contentHash(problem);
    const prior = manifest.problems[problem.slug];
    const payload = publicPayload(problem);
    try {
      if (prior?.id && prior.hash === hash) {
        report.results.push({ slug: problem.slug, status: "skipped-unchanged", id: prior.id });
        slugToId[problem.slug] = prior.id;
        continue;
      }
      if (prior?.id) {
        await apiJson(gate.apiBase, "PATCH", `/api/problems/${prior.id}/content`, {
          token,
          data: payload,
          expectedStatuses: [200],
        });
        manifest.problems[problem.slug] = {
          id: prior.id,
          numericId: prior.numericId,
          title: problem.title,
          hash,
        };
        slugToId[problem.slug] = prior.id;
        report.results.push({ slug: problem.slug, status: "updated", id: prior.id });
        continue;
      }
      const foreignId = existingTitles.get(problem.title);
      if (foreignId && !Object.values(manifest.problems).some((entry) => entry.id === foreignId)) {
        report.results.push({
          slug: problem.slug,
          status: "conflict-unmanaged-title",
          existingId: foreignId,
        });
        continue;
      }
      const created = await apiJson(gate.apiBase, "POST", "/api/problems", {
        token,
        data: payload,
        expectedStatuses: [200],
      });
      const id = created.body?.id;
      if (!id) throw new Error("create response missing id");
      manifest.problems[problem.slug] = {
        id,
        numericId: created.body?.numericId,
        title: problem.title,
        hash,
      };
      slugToId[problem.slug] = id;
      report.results.push({ slug: problem.slug, status: "created", id });
    } catch (error) {
      report.results.push({ slug: problem.slug, status: "failed", error: error.message });
    }
  }

  for (const bundle of bundles) {
    const problemIds = bundle.problemSlugs.map((slug) => slugToId[slug]).filter(Boolean);
    if (problemIds.length !== bundle.problemSlugs.length) {
      report.results.push({ bundle: bundle.name, status: "skipped-incomplete-problems" });
      continue;
    }
    const body = {
      name: bundle.name,
      description: bundle.description,
      difficulty: bundle.difficulty,
      category: bundle.category,
      tags: bundle.tags,
      problemIds,
      totalProblems: problemIds.length,
      totalPoints: bundle.totalPoints,
      estimatedTotalTime: bundle.estimatedTotalTime,
      isPremium: false,
      price: 0,
      visibility: "PUBLIC",
      isActive: true,
    };
    try {
      const prior = manifest.bundles[bundle.category];
      if (prior?.id) {
        report.results.push({ bundle: bundle.name, status: "bundle-exists", id: prior.id, note: "manual PATCH not implemented; IDs recorded" });
        continue;
      }
      const created = await apiJson(gate.apiBase, "POST", "/api/bundles", {
        token,
        data: body,
        expectedStatuses: [200],
      });
      const id = created.body?.id;
      manifest.bundles[bundle.category] = { id, name: bundle.name, problemIds };
      report.results.push({ bundle: bundle.name, status: "created", id });
    } catch (error) {
      report.results.push({ bundle: bundle.name, status: "failed", error: error.message });
    }
  }

  mkdirSync(dirname(manifestPath), { recursive: true });
  writeFileSync(manifestPath, JSON.stringify(manifest, null, 2));
  const reportPath = resolve(root, `.local/problem-bank/reports/phase${args.phase}-apply.json`);
  mkdirSync(dirname(reportPath), { recursive: true });
  writeFileSync(reportPath, JSON.stringify(report, null, 2));
  console.log(`Apply finished. Manifest and report written under .local/problem-bank/.`);
  const failed = report.results.filter((r) => String(r.status).includes("fail") || r.status === "conflict-unmanaged-title");
  process.exit(failed.length ? 1 : 0);
}

main().catch((error) => {
  console.error(error.message || "seed failed");
  process.exit(1);
});
