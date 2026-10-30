import { test, expect } from "@playwright/test";

test("five synchronous submit clicks send one submission job", async ({ page }) => {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const jwt = `${encode({ alg: "none" })}.${encode({ sub: "student", role: "USER", uid: "student-1" })}.sig`;
  await page.addInitScript((token) => {
    localStorage.setItem("jwtToken", token);
  }, jwt);

  let submits = 0;
  await page.route("**/api/**", async (route) => {
    const url = route.request().url();
    const method = route.request().method();
    if (url.includes("/api/validate-token")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ username: "student", role: "USER", id: "student-1" }),
      });
      return;
    }
    if (url.includes("/api/submissions/limits")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          canSubmitNow: true,
          hasExceededDailyLimit: false,
          hasExceededProblemLimit: false,
          remainingDaily: 10,
          remainingProblem: 10,
          cooldownSeconds: 0,
        }),
      });
      return;
    }
    if (method === "POST" && url.includes("/api/submission-jobs") && !url.includes("/events")) {
      submits += 1;
      await new Promise((resolve) => setTimeout(resolve, 2000));
      await route.fulfill({
        status: 202,
        contentType: "application/json",
        body: JSON.stringify({ jobId: "job-once", status: "QUEUED" }),
      });
      return;
    }
    if (method === "GET" && url.includes("/api/submission-jobs/job-once")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ jobId: "job-once", status: "ACCEPTED", testResults: [] }),
      });
      return;
    }
    if (url.includes("/api/problems/p1")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          id: "p1",
          title: "Add",
          description: "Add two numbers",
          difficulty: "EASY",
          executionMode: "STDIN_STDOUT",
          publicTestCases: [{ input: "1", output: "1" }],
          hiddenTestCases: [],
        }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });

  await page.goto("/problem/p1");
  const submit = page.locator("button.bg-blue-500");
  await expect(submit).toBeEnabled();
  await submit.evaluate((button) => {
    button.click();
    button.click();
    button.click();
    button.click();
    button.click();
  });
  await expect(submit).toHaveText("Submitting...");
  await expect.poll(() => submits, { timeout: 10000 }).toBe(1);
  await page.waitForTimeout(2500);
  expect(submits).toBe(1);
});
