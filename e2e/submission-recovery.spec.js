import { test, expect } from "@playwright/test";

const draftKey = "coderzclub:draft:v1:student-1:practice:p1:71";
const pythonStarter = /# Your code goes here/;

async function editor(page, { local = null, cloud = null, language = null, status = 503, limitsAfterFailure = null } = {}) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const jwt = `${encode({ alg: "none" })}.${encode({ sub: "student", role: "USER", uid: "student-1" })}.sig`;
  await page.addInitScript(({ jwt, local, language, draftKey }) => {
    localStorage.setItem("jwtToken", jwt);
    if (local) localStorage.setItem(draftKey, JSON.stringify(local));
    if (language) localStorage.setItem("coderzclub:editor-language:v1:practice:p1", language);
  }, { jwt, local, language, draftKey });
  const attempts = [];
  await page.route("**/api/**", async (route) => {
    const url = route.request().url();
    let body = {};
    let code = 200;
    if (url.includes("/validate-token")) body = { username: "student", role: "USER", id: "student-1" };
    else if (url.includes("/submissions/limits")) body = attempts.length > 0 && limitsAfterFailure
      ? limitsAfterFailure : { canSubmitNow: true, remainingDaily: 10, remainingProblem: 10 };
    else if (url.includes("/submission-jobs") && route.request().method() === "POST") {
      attempts.push(route.request().headers()["idempotency-key"]);
      code = status;
      body = { error: "MongoDB OperationNotSupportedInTransaction code 263 cluster.internal:27017" };
    } else if (url.includes("/draft")) body = cloud || { code: null, revision: 0 };
    else if (url.includes("/problems/p1")) body = {
      id: "p1", title: "Add", description: "Add two numbers", difficulty: "EASY",
      template: "", executionMode: "STDIN_STDOUT", publicTestCases: [{ input: "1", output: "1" }],
    };
    await route.fulfill({ status: code, contentType: "application/json", body: JSON.stringify(body) });
  });
  await page.goto("/problem/p1");
  const source = page.locator("textarea").first();
  await expect(source).toBeVisible();
  return { source, attempts, submit: page.locator("button.bg-blue-500") };
}

test("new user receives the Python starter", async ({ page }) => {
  const { source } = await editor(page);
  await expect(source).toHaveValue(pythonStarter);
});

test("pristine placeholder initializes the Python starter", async ({ page }) => {
  const { source } = await editor(page, { local: { version: 1, code: "", dirty: false, baseRevision: 0 } });
  await expect(source).toHaveValue(pythonStarter);
});

test("unsupported stored language falls back to Python with a starter", async ({ page }) => {
  const { source } = await editor(page, { language: "999" });
  await expect(source).toHaveValue(pythonStarter);
});

test("unsupported language fallback preserves saved Python and unsupported-language drafts", async ({ page }) => {
  const unsupportedKey = draftKey.replace(/:71$/, ":999");
  await page.addInitScript(({ unsupportedKey }) => {
    localStorage.setItem(unsupportedKey, JSON.stringify({ version: 1, code: "saved unsupported code", dirty: true }));
  }, { unsupportedKey });
  const code = "print('saved python')";
  const { source } = await editor(page, { language: "999", local: { version: 1, code, dirty: true } });
  await expect(source).toHaveValue(code);
  expect(await page.evaluate((key) => JSON.parse(localStorage.getItem(key)).code, draftKey)).toBe(code);
  expect(await page.evaluate((key) => JSON.parse(localStorage.getItem(key)).code, unsupportedKey)).toBe("saved unsupported code");
});

for (const code of ["", "print('saved work')"]) {
  test(`saved local draft is preserved: ${code ? "code" : "intentional blank"}`, async ({ page }) => {
    const { source } = await editor(page, { local: {
      version: 1, code, dirty: true, baseRevision: 0, localUpdatedAt: "2026-11-01T00:00:00Z",
    } });
    await expect(source).toHaveValue(code);
  });
}

test("saved empty cloud draft is preserved", async ({ page }) => {
  const { source } = await editor(page, { cloud: { code: "", revision: 2, updatedAt: "2026-11-01T00:00:00Z" } });
  await expect(source).toHaveValue("");
});

test("server failure hides database details and retry retains the operation key", async ({ page }) => {
  const { source, submit, attempts } = await editor(page);
  await expect(source).toHaveValue(pythonStarter);
  await submit.click();
  await expect(page.getByText("The submission outcome is uncertain. Retry checks the same attempt and does not start a new one.")).toBeVisible();
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect.poll(() => attempts.length).toBe(2);
  expect(attempts[0]).toBeTruthy();
  expect(attempts[1]).toBe(attempts[0]);
  await expect(page.locator("body")).not.toContainText("MongoDB");
  await expect(page.locator("body")).not.toContainText("cluster.internal");
});

test("legacy HTTP 400 database response is also hidden", async ({ page }) => {
  const { submit } = await editor(page, { status: 400 });
  await submit.click();
  await expect(page.getByText("Submission could not be accepted. Check your code and selected language, then try again.")).toBeVisible();
  await expect(page.locator("body")).not.toContainText("MongoDB");
});

test("retry after uncertain admission preserves its key despite cooldown and expired client timer", async ({ page }) => {
  const { submit, attempts } = await editor(page, { limitsAfterFailure: {
    canSubmitNow: false, cooldownSeconds: 2, hasExceededDailyLimit: true, remainingDaily: 0,
  } });
  await submit.click();
  await expect(page.getByText("The submission outcome is uncertain. Retry checks the same attempt and does not start a new one.")).toBeVisible();
  await page.evaluate(() => {
    const key = "cz-submission-attempt::p1";
    const attempt = JSON.parse(localStorage.getItem(key));
    attempt.at = Date.now() - 5 * 60 * 1000;
    localStorage.setItem(key, JSON.stringify(attempt));
  });
  await expect(submit).toBeEnabled();
  await submit.click();
  await expect.poll(() => attempts.length).toBe(2);
  expect(attempts[1]).toBe(attempts[0]);
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem("cz-submission-attempt::p1")).settled)).toBe(false);
});
