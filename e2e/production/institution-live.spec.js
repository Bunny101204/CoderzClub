import { test, expect } from "@playwright/test";
import { Buffer } from "node:buffer";
import { randomUUID } from "node:crypto";
import process from "node:process";
import { apiCall, loginAccount } from "./helpers/api.js";
import { loadCredentials, secretValues } from "./helpers/credentials.js";
import {
  INITIAL_STUDENT_CONCURRENCY,
  PRODUCTION_API_ORIGIN,
  PRODUCTION_WEB_ORIGIN,
  assertProductionOrigins,
  productionWriteGate,
  selectedStudents,
} from "./helpers/gates.js";
import { EvidenceCollector, captureScreenshot } from "./helpers/observability.js";
import { ResourceLedger } from "./helpers/resourceLedger.js";

const writeGate = productionWriteGate();

function runNames(runId) {
  const suffix = runId.toLowerCase().replace(/[^a-z0-9-]/g, "-").replace(/-+/g, "-").slice(0, 45);
  return {
    organization: `PW Production Test ${runId}`.slice(0, 120),
    slug: `pw-prod-${suffix}`.slice(0, 80),
    batch: `PW Batch ${runId}`.slice(0, 100),
    contest: `PW Contest ${runId}`.slice(0, 120),
  };
}

async function uiLogin(page, account) {
  await page.goto("/auth");
  await page.getByPlaceholder("Enter your username").fill(account.username);
  await page.getByPlaceholder("Enter your password").fill(account.password);
  await page.getByRole("button", { name: /^Sign In$/i }).click();
  await expect(page).not.toHaveURL(/\/auth$/, { timeout: 20_000 });
}

async function selectOrganization(page, organizationId) {
  const switcher = page.getByTestId("organization-switcher");
  await expect(switcher).toBeVisible();
  if (await switcher.isDisabled()) {
    await expect(switcher).toHaveValue(organizationId);
  } else {
    await switcher.selectOption(organizationId);
    await expect(switcher).toHaveValue(organizationId);
  }
}

async function browserApi(page, { method = "GET", path, organizationId, data, expectedStatuses = [200] }) {
  const result = await page.evaluate(async (input) => {
    const token = localStorage.getItem("jwtToken") || localStorage.getItem("token");
    if (!token) return { status: 0, body: null };
    const response = await fetch(`${input.apiOrigin}${input.path}`, {
      method: input.method,
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
        ...(input.organizationId ? { "X-Organization-Id": input.organizationId } : {}),
        ...(input.idempotencyKey ? { "Idempotency-Key": input.idempotencyKey } : {}),
      },
      body: input.data === undefined ? undefined : JSON.stringify(input.data),
    });
    const text = await response.text();
    let body = text;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      // Text responses are valid for CSV and a few legacy endpoints.
    }
    return { status: response.status, body };
  }, {
    apiOrigin: PRODUCTION_API_ORIGIN,
    method,
    path,
    organizationId,
    data,
    idempotencyKey: method === "POST" ? randomUUID() : undefined,
  });
  if (!expectedStatuses.includes(result.status)) {
    throw new Error(`${method} ${new URL(path, PRODUCTION_API_ORIGIN).pathname} returned HTTP ${result.status}.`);
  }
  return result;
}

async function waitForJob(page, jobId, organizationId, timeoutMs = 180_000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    const response = await browserApi(page, {
      path: `/api/submission-jobs/${encodeURIComponent(jobId)}`,
      organizationId,
    });
    if (response.body?.status === "COMPLETED" || response.body?.status === "FAILED") {
      return { ...response.body, durationMs: Date.now() - started };
    }
    await page.waitForTimeout(2_000);
  }
  throw new Error("A Judge0-backed submission job did not reach a terminal state before the safety timeout.");
}

if (process.env.PRODUCTION_E2E_CONFIG === "true") {
test.describe("opt-in live production institution workflow", () => {
  test.describe.configure({ mode: "serial" });
  let credentials;

  test.beforeAll(() => {
    assertProductionOrigins(PRODUCTION_WEB_ORIGIN, PRODUCTION_API_ORIGIN);
    credentials = loadCredentials();
  });

  test("synthetic role accounts authenticate and production prerequisites are readable", async ({ request }, testInfo) => {
    const collector = new EvidenceCollector(secretValues(credentials));
    try {
      const accounts = [
        ["platform admin", credentials.platformAdmin, "ADMIN"],
        ["organization owner", credentials.organizationOwner, "USER"],
        ["coordinator", credentials.coordinator, "USER"],
        ...credentials.students.map((account, index) => [`student ${index + 1}`, account, "USER"]),
        ["unenrolled student", credentials.unenrolledStudent, "USER"],
        ["college B outsider", credentials.collegeBOutsider, "USER"],
      ];

      for (const [, account, expectedRole] of accounts) {
        const authenticated = await loginAccount(request, account, collector.timings);
        expect(String(authenticated.role).toUpperCase()).toBe(expectedRole);
      }

      const outsider = await loginAccount(request, credentials.collegeBOutsider, collector.timings);
      await apiCall(request, "GET", "/api/organizations/active-context", {
        token: outsider.token,
        organizationId: credentials.collegeBOutsider.organizationId,
        expectedStatuses: [200],
        metrics: collector.timings,
      });
    } finally {
      await collector.attach(testInfo);
    }
  });

  test("owner-approved write run creates isolated resources and verifies concurrent judging", async ({ page, request, browser }, testInfo) => {
    test.skip(!writeGate.allowed, `Production writes are disabled: ${writeGate.reasons.join("; ")}`);
    test.setTimeout(6 * 60_000);

    const runId = process.env.PRODUCTION_E2E_RUN_ID;
    const names = runNames(runId);
    const collector = new EvidenceCollector(secretValues(credentials));
    const ledger = new ResourceLedger(runId);
    const studentContexts = [];
    let ownerTracing = false;
    collector.attachPage(page, "organization-owner");

    try {
      const students = selectedStudents(credentials.students);
      expect(students).toHaveLength(INITIAL_STUDENT_CONCURRENCY);

      const admin = await loginAccount(request, credentials.platformAdmin, collector.timings);
      const owner = await loginAccount(request, credentials.organizationOwner, collector.timings);
      const coordinator = await loginAccount(request, credentials.coordinator, collector.timings);
      const studentAccounts = [];
      for (const student of students) {
        studentAccounts.push({ account: student, auth: await loginAccount(request, student, collector.timings) });
      }
      const unenrolled = await loginAccount(request, credentials.unenrolledStudent, collector.timings);
      const outsider = await loginAccount(request, credentials.collegeBOutsider, collector.timings);

      const organizationsBefore = await apiCall(request, "GET", "/api/organizations", {
        token: admin.token,
        expectedStatuses: [200],
        metrics: collector.timings,
      });
      expect((organizationsBefore.body?.organizations || []).some((org) => org.slug === names.slug)).toBe(false);
      let organizationResponse;
      try {
        organizationResponse = await apiCall(request, "POST", "/api/organizations", {
          token: admin.token,
          data: {
            name: names.organization,
            slug: names.slug,
            category: "EDUCATION",
            ownerUserId: owner.profile.id,
          },
          expectedStatuses: [200],
          metrics: collector.timings,
        });
      } catch (error) {
        // A lost response can follow a successful write. Recover the run-scoped
        // id into the ledger before failing rather than leaving an untracked org.
        const recovery = await apiCall(request, "GET", "/api/organizations", {
          token: admin.token,
          expectedStatuses: [200],
          metrics: collector.timings,
        });
        const recovered = (recovery.body?.organizations || []).find((org) => org.slug === names.slug);
        if (recovered?.id) ledger.record("organization", recovered.id, names.organization);
        throw error;
      }
      const organizationId = organizationResponse.body?.id;
      expect(organizationId).toBeTruthy();
      ledger.record("organization", organizationId, names.organization);
      ledger.record("organization-membership", `${organizationId}:${owner.profile.id}`, "organization owner");

      const members = [
        { id: coordinator.profile.id, roles: ["INSTRUCTOR"] },
        ...studentAccounts.map(({ auth }) => ({ id: auth.profile.id, roles: ["STUDENT"] })),
        { id: unenrolled.profile.id, roles: ["STUDENT"] },
      ];
      for (const member of members) {
        await apiCall(request, "POST", `/api/organizations/${organizationId}/members`, {
          token: owner.token,
          data: { userId: member.id, roles: member.roles },
          expectedStatuses: [200],
          metrics: collector.timings,
        });
        ledger.record("organization-membership", `${organizationId}:${member.id}`, member.roles.join(","));
      }

      await uiLogin(page, credentials.organizationOwner);
      await page.context().tracing.start({ screenshots: true, snapshots: true, sources: false });
      ownerTracing = true;
      await page.goto("/institution");
      await expect(page.getByTestId("institution-dashboard")).toBeVisible();
      await selectOrganization(page, organizationId);
      await expect(page.getByTestId("institution-brand-name")).toContainText(names.organization);
      await captureScreenshot(page, testInfo, "owner-institution-dashboard.png");

      await page.getByLabel("New batch name").fill(names.batch);
      await page.getByRole("button", { name: /create batch/i }).click();
      await expect(page.getByRole("link", { name: names.batch })).toBeVisible();
      const enrollSelect = page.getByLabel("Batch for enrollment");
      const batchId = await enrollSelect.locator("option").filter({ hasText: names.batch }).first().getAttribute("value");
      expect(batchId).toBeTruthy();
      ledger.record("batch", batchId, names.batch);
      await enrollSelect.selectOption(batchId);

      for (const { account, auth } of studentAccounts) {
        await page.getByLabel("Student username").fill(account.username);
        const enrollmentResponse = page.waitForResponse((response) =>
          response.request().method() === "POST" &&
          new URL(response.url()).pathname === `/api/admin/batches/${batchId}/members`
        );
        await page.getByRole("button", { name: /enroll student/i }).click();
        expect((await enrollmentResponse).ok()).toBeTruthy();
        await expect(page.getByTestId("institution-notice")).toContainText(/enrollment/i);
        ledger.record("batch-membership", `${batchId}:${auth.profile.id}`, "synthetic student");
      }

      await page.getByLabel("Coordinator user id").fill(coordinator.profile.id);
      const coordinatorResponse = page.waitForResponse((response) =>
        response.request().method() === "POST" &&
        new URL(response.url()).pathname === `/api/admin/batches/${batchId}/coordinators`
      );
      await page.getByRole("button", { name: /grant coordinator/i }).click();
      expect((await coordinatorResponse).ok()).toBeTruthy();
      await expect(page.getByTestId("institution-notice")).toContainText(/coordinator/i);
      ledger.record("batch-coordinator", `${batchId}:${coordinator.profile.id}`, "synthetic coordinator");

      await apiCall(request, "POST", `/api/admin/batches/${batchId}/assignments`, {
        token: owner.token,
        organizationId,
        data: { problemIds: [credentials.problem.id] },
        expectedStatuses: [200],
        metrics: collector.timings,
      });
      ledger.record("batch-assignment", `${batchId}:${credentials.problem.id}`, credentials.problem.title);
      const assignments = await apiCall(request, "GET", `/api/admin/batches/${batchId}/assignments`, {
        token: owner.token,
        organizationId,
        expectedStatuses: [200],
        metrics: collector.timings,
      });
      expect(JSON.stringify(assignments.body)).toContain(credentials.problem.id);

      await page.getByTestId(`batch-contest-${batchId}`).click();
      await expect(page.getByTestId("contest-form-title")).toBeVisible();
      const start = new Date(Date.now() - 2 * 60_000);
      const end = new Date(Date.now() + 60 * 60_000);
      const local = (date) => {
        const pad = (value) => String(value).padStart(2, "0");
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
      };
      await page.locator("#contest-title").fill(names.contest);
      await page.locator("#contest-description").fill(`Owner-approved Playwright production evidence run ${runId}`);
      await page.locator("#contest-start").fill(local(start));
      await page.locator("#contest-end").fill(local(end));
      await page.getByRole("button", { name: /add problems/i }).click();
      await page.locator("#contest-problem-search").fill(credentials.problem.title);
      await expect(page.getByText(credentials.problem.title, { exact: true }).first()).toBeVisible();
      await page.getByRole("button", { name: /^Add$/ }).first().click();
      await page.getByRole("button", { name: /^Done$/ }).click();
      await page.getByRole("button", { name: /save draft/i }).click();
      await expect(page).toHaveURL(/\/institution\/contests\/(?!new(?:\?|$))[a-zA-Z0-9_-]+(?:\?.*)?$/);
      const contestId = new URL(page.url()).pathname.split("/").pop();
      ledger.record("contest", contestId, names.contest);
      await page.getByRole("button", { name: /^Publish$/ }).click();
      await expect(page.getByText(/Published\.?/i)).toBeVisible();

      const sessions = [];
      for (let index = 0; index < studentAccounts.length; index += 1) {
        const context = await browser.newContext();
        const trackedContext = { context, index: index + 1, tracing: false };
        studentContexts.push(trackedContext);
        const studentPage = await context.newPage();
        collector.attachPage(studentPage, `student-${index + 1}`);
        await uiLogin(studentPage, studentAccounts[index].account);
        await context.tracing.start({ screenshots: true, snapshots: true, sources: false });
        trackedContext.tracing = true;
        await selectOrganization(studentPage, organizationId);
        await studentPage.goto(`/contests/${contestId}`);
        await expect(studentPage.getByText(names.contest, { exact: true })).toBeVisible();
        sessions.push({ page: studentPage, account: studentAccounts[index].account, expectsAccepted: index % 2 === 0 });
      }
      await captureScreenshot(sessions[0].page, testInfo, "student-contest-entry.png");

      const admissionStarted = Date.now();
      const admissions = await Promise.all(sessions.map(async (session) => {
        const response = await browserApi(session.page, {
          method: "POST",
          path: `/api/contests/${contestId}/problems/${encodeURIComponent(credentials.problem.id)}/submission-jobs`,
          organizationId,
          data: {
            code: session.expectsAccepted ? credentials.problem.correctCode : credentials.problem.incorrectCode,
            language: credentials.problem.language,
            languageId: credentials.problem.languageId,
            codingDurationSeconds: 30,
          },
          expectedStatuses: [202],
        });
        expect(response.body?.jobId).toBeTruthy();
        return { ...session, jobId: response.body.jobId };
      }));
      collector.timing("five-student-concurrent-admission", Date.now() - admissionStarted, {
        students: admissions.length,
      });

      const verdicts = await Promise.all(admissions.map(async (admission) => {
        const result = await waitForJob(admission.page, admission.jobId, organizationId);
        collector.timing("judge0-job-completion", result.durationMs, {
          expected: admission.expectsAccepted ? "ACCEPTED" : "WRONG_ANSWER",
          actual: result.result || result.status,
        });
        return { ...admission, result };
      }));
      for (const verdict of verdicts) {
        if (verdict.expectsAccepted) {
          expect(verdict.result.status).toBe("COMPLETED");
          expect(verdict.result.result).toBe("ACCEPTED");
        } else {
          expect(verdict.result.status).toBe("COMPLETED");
          expect(verdict.result.result).toBe(credentials.problem.incorrectExpectedVerdict || "WRONG_ANSWER");
        }
      }

      const acceptedUsernames = verdicts
        .filter((item) => item.expectsAccepted)
        .map((item) => item.account.username);
      let leaderboard;
      await expect.poll(async () => {
        leaderboard = await browserApi(sessions[0].page, {
          path: `/api/contests/${contestId}/leaderboard?page=0&size=50`,
          organizationId,
        });
        const entries = leaderboard.body?.entries || [];
        return acceptedUsernames.every((username) =>
          entries.some((entry) => entry.username === username && entry.totalPoints > 0 && entry.solvedCount > 0)
        );
      }, { timeout: 60_000, intervals: [2_000, 5_000] }).toBe(true);
      await sessions[0].page.reload();
      await expect(sessions[0].page.getByText(names.contest, { exact: true })).toBeVisible();
      await captureScreenshot(sessions[0].page, testInfo, "contest-leaderboard.png");

      const report = await apiCall(request, "GET", `/api/admin/batches/${batchId}/report`, {
        token: owner.token,
        organizationId,
        expectedStatuses: [200],
        metrics: collector.timings,
      });
      expect(report.body).toBeTruthy();
      const csv = await apiCall(request, "GET", `/api/admin/batches/${batchId}/report.csv`, {
        token: owner.token,
        organizationId,
        expectedStatuses: [200],
        metrics: collector.timings,
      });
      expect(String(csv.body)).toContain(acceptedUsernames[0]);
      await page.goto("/institution");
      await selectOrganization(page, organizationId);
      await page.getByTestId(`batch-report-${batchId}`).click();
      await expect(page.getByTestId("institution-batch-report")).toBeVisible();
      await captureScreenshot(page, testInfo, "batch-performance-report.png");

      await apiCall(request, "GET", `/api/admin/batches/${batchId}/report`, {
        token: coordinator.token,
        organizationId,
        expectedStatuses: [200],
        metrics: collector.timings,
      });
      await apiCall(request, "GET", `/api/organizations/${organizationId}/members`, {
        token: coordinator.token,
        organizationId,
        expectedStatuses: [403],
        metrics: collector.timings,
      });
      await apiCall(request, "GET", `/api/contests/${contestId}`, {
        token: unenrolled.token,
        organizationId,
        expectedStatuses: [403, 404],
        metrics: collector.timings,
      });
      await apiCall(request, "GET", `/api/admin/batches/${batchId}`, {
        token: outsider.token,
        organizationId: credentials.collegeBOutsider.organizationId,
        expectedStatuses: [403, 404],
        metrics: collector.timings,
      });
      await apiCall(request, "GET", `/api/organizations/${organizationId}`, {
        token: outsider.token,
        organizationId,
        expectedStatuses: [403, 404],
        metrics: collector.timings,
      });
    } finally {
      await testInfo.attach("resource-ledger.json", {
        body: Buffer.from(JSON.stringify(ledger.snapshot(), null, 2)),
        contentType: "application/json",
      });
      if (ownerTracing) {
        try {
          const ownerTrace = testInfo.outputPath("owner-workflow-trace.zip");
          await page.context().tracing.stop({ path: ownerTrace });
          await testInfo.attach("owner-workflow-trace.zip", {
            path: ownerTrace,
            contentType: "application/zip",
          });
        } catch {
          collector.browserErrors.push({ label: "organization-owner", type: "trace", message: "Trace capture failed." });
        }
      }
      for (const tracked of studentContexts) {
        try {
          if (tracked.tracing) {
            const tracePath = testInfo.outputPath(`student-${tracked.index}-workflow-trace.zip`);
            await tracked.context.tracing.stop({ path: tracePath });
            await testInfo.attach(`student-${tracked.index}-workflow-trace.zip`, {
              path: tracePath,
              contentType: "application/zip",
            });
          }
        } catch {
          collector.browserErrors.push({ label: `student-${tracked.index}`, type: "trace", message: "Trace capture failed." });
        } finally {
          await tracked.context.close();
        }
      }
      await collector.attach(testInfo);
    }
  });
});
}
