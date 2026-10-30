import { test, expect } from "@playwright/test";

async function signIn(page, { username, role, id }) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  const jwt = `${encode({ alg: "none" })}.${encode({ sub: username, role, uid: id })}.sig`;
  await page.addInitScript((token) => {
    localStorage.setItem("jwtToken", token);
  }, jwt);
  await page.route("**/api/**", async (route) => {
    const url = route.request().url();
    if (url.includes("/api/validate-token")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ username, role, id }),
      });
      return;
    }
    if (url.includes("/api/problems")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          problems: [{
            id: "p1",
            numericId: 1,
            title: "Reverse Array With A Very Long Title For A Narrow Screen",
            difficulty: "EASY",
            tags: ["arrays", "basic", "reverse"],
          }],
          totalPages: 1,
          totalItems: 1,
          currentPage: 0,
        }),
      });
      return;
    }
    if (url.includes("/api/admin/batches")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          batches: [{ id: "b1", name: "batch-1", active: true, memberCount: 1, assignmentCount: 0 }],
          totalItems: 1,
        }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });
}

test("legacy problem route keeps the id", async ({ page }) => {
  await page.route("**/api/**", async (route) => {
    await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
  });
  await page.goto("/problem-old/42");
  await expect(page).toHaveURL(/\/problem\/42$/);
});

test("unknown pages stay on a not-found screen", async ({ page }) => {
  await page.goto("/this-route-does-not-exist");
  await expect(page.getByRole("heading", { name: "Page not found" })).toBeVisible();
  await expect(page).toHaveURL(/this-route-does-not-exist/);
});

test("phone problem list does not widen the document", async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 700 });
  await signIn(page, { username: "student", role: "USER", id: "student-1" });
  await page.goto("/home");
  await expect(page.getByRole("button", { name: /Reverse Array With A Very Long Title/ })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(1);
});

test("admin batch count stays unknown until the list loads", async ({ page }) => {
  await signIn(page, { username: "admin", role: "ADMIN", id: "admin-1" });
  await page.goto("/admin");
  await expect(page.getByRole("button", { name: "Batches", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Batches", exact: true }).click();
  await expect(page.getByRole("button", { name: "Batches (1)" })).toBeVisible();
  await expect(page.getByText("batch-1")).toBeVisible();
});

test("a user is denied the admin dashboard without a redirect loop", async ({ page }) => {
  await signIn(page, { username: "student", role: "USER", id: "student-1" });
  await page.goto("/admin");
  await expect(page.getByRole("heading", { name: "Access denied" })).toBeVisible();
  await expect(page).toHaveURL(/\/admin$/);
});
