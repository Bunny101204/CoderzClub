# Phase 6.5 — Release Hardening

Scope: staging-only. No production import or deploy.

## Verdict

**PASS** for release hardening of the DSA problem-bank worker path, with
explicit language gating for unverified Judge0 runtimes.

## Languages

| Judge0 ID | Language | Student-enabled | Live-verified | Evidence |
| ---: | --- | --- | --- | --- |
| 71 | Python 3.8.1 | Yes | Yes | Hello-world Accepted; Phase 6 187/187 references |
| 54 | C++ (GCC 9.2.0) | Yes | Yes | Hello-world Accepted; Phase 6 multi-lang samples |
| 62 | Java (OpenJDK 13) | **No** | No | Hello-world Compilation Error: cannot reserve 256000KB heap inside local isolate |
| 63 | JavaScript (Node 12) | **No** | No | Hello-world Time Limit Exceeded at ~0.003s (isolate wall-clock), not solution logic |
| 50+ | C, C#, TS, Go, PHP, Ruby, Rust, Kotlin, Scala, Swift | **No** | No | Catalog placeholders; never live-verified |

Do **not** raise production sandbox limits to “make Java/Node work” on the
laptop isolate. Re-enable only after a production-class Judge0 probe passes.

## Fixes / tests shipped

1. Judge0 stdin payload preserves `null` (omit), `""`, whitespace-only, leading/trailing, and multiline stdin.
2. Character-frequency ordering contract (character-code ascending) covered by Node regression tests only — no public reusable oracle module.
3. `LanguageCapabilityCatalog` enables only Python + C++; API rejects other language IDs.
4. Editor language selectors limited to enabled languages (default Python).

## Test runs (Phase 6.5)

- Backend targeted Maven suite: Judge0 stdin/routing, language catalog, harness, compatibility — passed
- Frontend Node suites (`test:*` package scripts listed in Phase 6.5 CI set) — passed
- Problem-bank guards + character-frequency oracle tests — passed
- Staging hello-world probes recorded under gitignored `.local/problem-bank/reports/phase6-5/`
- Live staging gate check: `/api/languages` enabled=`[71,54]`; Java/Node submission-jobs → HTTP 400; Python character-frequency → ACCEPTED 8/8
- Seeder idempotency (phase 1 re-apply): 34 skipped-unchanged, 3 bundle-exists; catalog remains 187/17

## Isolation

- Compose project `cz-pb-staging` / API `127.0.0.1:18090`
- Private bank (oracles, hidden cases, manifests, credentials) remains under `.local/problem-bank/`
- Local Judge0 cgroup workaround stays staging-only
