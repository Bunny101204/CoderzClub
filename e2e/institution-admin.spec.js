import { test, expect } from "@playwright/test";
import {
  liveInstitutionE2EEnabled,
  liveInstitutionPrerequisiteError,
  loadInstitutionEnv,
} from "./helpers/institutionEnv.js";
import { apiJson, loginApi, toDatetimeLocal } from "./helpers/institutionApi.js";

const liveEnabled = liveInstitutionE2EEnabled();
if (liveEnabled) {
  loadInstitutionEnv();
}
const ownerUser = process.env.E2E_ORG_OWNER_USER;
const ownerPass = process.env.E2E_ORG_OWNER_PASS;
const orgAId = process.env.E2E_ORG_A_ID;
const orgBId = process.env.E2E_ORG_B_ID;
const orgAName = process.env.E2E_ORG_A_NAME;
const orgBName = process.env.E2E_ORG_B_NAME;
const ownerBUser = process.env.E2E_ORG_B_OWNER_USER;
const ownerBPass = process.env.E2E_ORG_B_OWNER_PASS;
const instructorUser = process.env.E2E_INSTRUCTOR_A_USER;
const instructorPass = process.env.E2E_INSTRUCTOR_A_PASS;
const instructorId = process.env.E2E_INSTRUCTOR_A_ID;
const studentA1User = process.env.E2E_STUDENT_A1_USER;
const studentA1Pass = process.env.E2E_STUDENT_A1_PASS;
const studentA1Id = process.env.E2E_STUDENT_A1_ID;
const studentA2User = process.env.E2E_STUDENT_A2_USER;
const studentA2Pass = process.env.E2E_STUDENT_A2_PASS;
const studentB1User = process.env.E2E_STUDENT_B1_USER;
const studentB1Pass = process.env.E2E_STUDENT_B1_PASS;
const multiUser = process.env.E2E_MULTI_USER;
const multiPass = process.env.E2E_MULTI_PASS;
const problemOrgATitle = process.env.E2E_PROBLEM_ORG_A_TITLE;
const problemOrgAId = process.env.E2E_PROBLEM_ORG_A_ID;

async function uiLogin(page, username, password) {
  await page.goto("/auth");
  await page.getByPlaceholder("Enter your username").fill(username);
  await page.getByPlaceholder("Enter your password").fill(password);
  await page.getByRole("button", { name: /^Sign In$/i }).click();
  await expect(page).not.toHaveURL(/\/auth$/, { timeout: 20000 });
}

async function selectOrganization(page, organizationId) {
  const switcher = page.getByTestId("organization-switcher");
  if (!(await switcher.count())) {
    return;
  }
  // Single-org members get a disabled switcher that already reflects the active org.
  if (await switcher.isDisabled()) {
    await expect(switcher).toHaveValue(organizationId);
    return;
  }
  await switcher.selectOption(organizationId);
  await expect(switcher).toHaveValue(organizationId);
}

test("public pages keep default CoderzClub branding without an active organization", async ({ page }) => {
  await page.goto("/about");
  await expect(page.getByRole("link", { name: /CoderzClub/i }).first()).toBeVisible();
  await expect(page.getByTestId("organization-switcher")).toHaveCount(0);
});

test.describe("two-organization institution workflow", () => {
  test.describe.configure({ mode: "serial" });
  test.skip(!liveEnabled, "Set INSTITUTION_E2E_ENABLED=true, seed fixtures, and start the backend to run this live workflow");
  test.setTimeout(120_000);

  test.beforeAll(async () => {
    if (!liveEnabled) return;
    const error = await liveInstitutionPrerequisiteError();
    if (error) {
      throw new Error(error);
    }
  });

  let createdBatchId = "";
  let createdContestId = "";
  const batchName = `E2E Batch ${Date.now()}`;
  const contestTitle = `E2E Contest ${Date.now()}`;

  test("owner A creates batch, enrolls, assigns coordinator, schedules contest, and views report", async ({ page, request }) => {
    await uiLogin(page, ownerUser, ownerPass);
    await page.goto("/institution");
    await expect(page.getByTestId("institution-dashboard")).toBeVisible({ timeout: 20000 });
    await selectOrganization(page, orgAId);
    await expect(page.getByTestId("institution-brand-name")).toContainText(/Alpha E2E/i);

    await page.getByLabel("New batch name").fill(batchName);
    await page.getByRole("button", { name: /create batch/i }).click();
    await expect(page.getByRole("link", { name: batchName })).toBeVisible({ timeout: 15000 });

    const enrollSelect = page.getByLabel("Batch for enrollment");
    await expect(enrollSelect).toBeVisible();
    const optionValue = await enrollSelect.locator("option").filter({ hasText: batchName }).first().getAttribute("value");
    expect(optionValue).toBeTruthy();
    createdBatchId = optionValue;
    await enrollSelect.selectOption(createdBatchId);

    await page.getByLabel("Student username").fill(studentA1User);
    await page.getByRole("button", { name: /enroll student/i }).click();
    await expect(page.getByTestId("institution-notice")).toContainText(/enrollment/i, { timeout: 15000 });

    await page.getByLabel("Coordinator user id").fill(instructorId);
    await page.getByRole("button", { name: /grant coordinator/i }).click();
    await expect(page.getByTestId("institution-notice")).toContainText(/coordinator/i, { timeout: 15000 });

    await page.getByTestId(`batch-contest-${createdBatchId}`).click();
    await expect(page.getByTestId("contest-form-title")).toBeVisible({ timeout: 15000 });

    const start = new Date(Date.now() - 5 * 60 * 1000);
    const end = new Date(Date.now() + 2 * 60 * 60 * 1000);
    await page.locator("#contest-title").fill(contestTitle);
    await page.locator("#contest-description").fill("Institution E2E batch contest");
    await page.locator("#contest-start").fill(toDatetimeLocal(start));
    await page.locator("#contest-end").fill(toDatetimeLocal(end));

    await page.getByRole("button", { name: /add problems/i }).click();
    await page.locator("#contest-problem-search").fill(problemOrgATitle);
    await expect(page.getByText(problemOrgATitle)).toBeVisible({ timeout: 15000 });
    await page.getByRole("button", { name: /^Add$/ }).first().click();
    await page.getByRole("button", { name: /^Done$/ }).click();

    await page.getByRole("button", { name: /save draft/i }).click();
    await expect(page).toHaveURL(/\/institution\/contests\/(?!new(?:\?|$))[a-zA-Z0-9_-]+(?:\?.*)?$/, { timeout: 20000 });
    createdContestId = new URL(page.url()).pathname.split("/").pop();
    expect(createdContestId).toBeTruthy();
    expect(createdContestId).not.toBe("new");

    await page.getByRole("button", { name: /^Publish$/ }).click();
    await expect(page.getByText(/Published\.?/i)).toBeVisible({ timeout: 15000 });

    await page.goto("/institution");
    await selectOrganization(page, orgAId);
    await page.getByTestId(`batch-report-${createdBatchId}`).click();
    await expect(page.getByTestId("institution-batch-report")).toBeVisible({ timeout: 15000 });

    const token = await loginApi(request, ownerUser, ownerPass);
    const contestGet = await apiJson(request, "GET", `/api/admin/contests/${createdContestId}`, {
      token,
      orgId: orgAId,
      expectedStatuses: [200],
    });
    expect(contestGet.json.published).toBeTruthy();
    expect(contestGet.json.batchIds).toContain(createdBatchId);
    expect(contestGet.json.problemIds || []).toContain(problemOrgAId);
  });

  test("eligible student can access contest; unauthorized students and org B cannot", async ({ page, request }) => {
    test.skip(!createdContestId, "Depends on contest created in prior test");
    const contestId = createdContestId;
    const batchId = createdBatchId;

    await uiLogin(page, studentA1User, studentA1Pass);
    await selectOrganization(page, orgAId);
    await page.goto(`/contests/${contestId}`);
    await expect(page.getByText(contestTitle)).toBeVisible({ timeout: 20000 });

    const eligibleToken = await loginApi(request, studentA1User, studentA1Pass);
    await apiJson(request, "GET", `/api/contests/${contestId}`, {
      token: eligibleToken,
      orgId: orgAId,
      expectedStatuses: [200],
    });

    const a2Token = await loginApi(request, studentA2User, studentA2Pass);
    const a2Denied = await apiJson(request, "GET", `/api/contests/${contestId}`, {
      token: a2Token,
      orgId: orgAId,
    });
    expect([403, 404]).toContain(a2Denied.status);

    const b1Token = await loginApi(request, studentB1User, studentB1Pass);
    const b1Denied = await apiJson(request, "GET", `/api/contests/${contestId}`, {
      token: b1Token,
      orgId: orgBId,
    });
    expect([403, 404]).toContain(b1Denied.status);

    const ownerBToken = await loginApi(request, ownerBUser, ownerBPass);
    const crossBatch = await apiJson(request, "GET", `/api/admin/batches/${batchId}`, {
      token: ownerBToken,
      orgId: orgBId,
    });
    expect([403, 404]).toContain(crossBatch.status);
    const crossContest = await apiJson(request, "GET", `/api/admin/contests/${contestId}`, {
      token: ownerBToken,
      orgId: orgBId,
    });
    expect([403, 404]).toContain(crossContest.status);
    const spoofOrg = await apiJson(request, "GET", `/api/admin/batches/${batchId}`, {
      token: ownerBToken,
      orgId: orgAId,
    });
    expect([403, 404]).toContain(spoofOrg.status);
  });

  test("instructor with coordinator grant can read permitted batch report only", async ({ request }) => {
    test.skip(!createdBatchId, "Depends on batch created in prior test");
    const batchId = createdBatchId;
    const token = await loginApi(request, instructorUser, instructorPass);
    await apiJson(request, "GET", `/api/admin/batches/${batchId}/report`, {
      token,
      orgId: orgAId,
      expectedStatuses: [200],
    });
    const foreign = await apiJson(request, "GET", `/api/organizations/${orgBId}/members`, {
      token,
      orgId: orgBId,
    });
    expect([403, 404]).toContain(foreign.status);
  });

  test("org B admin UI cannot see org A resources via switcher", async ({ page }) => {
    await uiLogin(page, ownerBUser, ownerBPass);
    await page.goto("/institution");
    await expect(page.getByTestId("institution-dashboard")).toBeVisible({ timeout: 20000 });
    await selectOrganization(page, orgBId);
    await expect(page.getByTestId("institution-brand-name")).toContainText(/Beta E2E/i);
    await expect(page.getByText(batchName)).toHaveCount(0);
    const switcher = page.getByTestId("organization-switcher");
    if (await switcher.count()) {
      const values = await switcher.locator("option").evaluateAll((nodes) => nodes.map((n) => n.value));
      expect(values).not.toContain(orgAId);
      expect(values).toContain(orgBId);
    }
  });

  test("multi-org user switches organizations and permissions refresh", async ({ page, request }) => {
    await uiLogin(page, multiUser, multiPass);
    await page.goto("/institution");
    await expect(page.getByTestId("institution-dashboard")).toBeVisible({ timeout: 20000 });
    const switcher = page.getByTestId("organization-switcher");
    await expect(switcher).toBeVisible();
    await switcher.selectOption(orgAId);
    await expect(page.getByTestId("institution-brand-name")).toContainText(/Alpha E2E/i);
    await expect(page.getByRole("heading", { name: /student workspace/i })).toBeVisible();
    await switcher.selectOption(orgBId);
    await expect(page.getByTestId("institution-brand-name")).toContainText(/Beta E2E/i);

    const token = await loginApi(request, multiUser, multiPass);
    await apiJson(request, "GET", "/api/organizations/active-context", {
      token,
      orgId: orgAId,
      expectedStatuses: [200],
    });
    await apiJson(request, "GET", "/api/organizations/active-context", {
      token,
      orgId: orgBId,
      expectedStatuses: [200],
    });
  });

  test("public platform catalog remains available", async ({ page, request }) => {
    await uiLogin(page, studentA1User, studentA1Pass);
    await page.goto("/problems");
    await expect(page).toHaveURL(/problems/i);
    const token = await loginApi(request, studentA1User, studentA1Pass);
    const catalog = await apiJson(request, "GET", "/api/problems?page=0&size=5", {
      token,
      expectedStatuses: [200],
    });
    expect(catalog.json).toBeTruthy();
  });
});
