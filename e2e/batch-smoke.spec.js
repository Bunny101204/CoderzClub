import { test, expect } from "@playwright/test";

function token(username, role, id) {
  const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "none" })}.${encode({ sub: username, role, uid: id })}.sig`;
}

async function signIn(page, { username, role, id }) {
  await page.addInitScript((jwt) => {
    localStorage.setItem("jwtToken", jwt);
  }, token(username, role, id));
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
    await route.fallback();
  });
}

test("editor language switch keeps each language draft", async ({ page }) => {
  await page.goto("/editor");
  const editor = page.locator("#codeArea");
  await expect(editor).toHaveAttribute("data-language", "62");
  await editor.fill("public class Main { /* student java */ }");
  await page.locator("select").first().selectOption("71");
  await expect(editor).toHaveAttribute("data-language", "71");
  await expect(editor).not.toHaveValue(/public class Main/);
  await expect(editor).toHaveValue(/# Your code goes here/);
  await editor.fill("print('python draft')");
  await page.locator("select").first().selectOption("62");
  await expect(editor).toHaveValue(/student java/);
  await page.locator("select").first().selectOption("71");
  await expect(editor).toHaveValue(/python draft/);
  await page.reload();
  await expect(editor).toHaveAttribute("data-language", "71");
});

test("admin batch page manages students from the mocked API", async ({ page }) => {
  await signIn(page, { username: "admin", role: "ADMIN", id: "admin-1" });
  await page.route("**/api/admin/batches/users**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        users: [{ id: "student-1", username: "ada", email: "ada@example.com", alreadyMember: false, accountStatus: "ACTIVE", emailVerified: true }],
      }),
    });
  });
  await page.route("**/api/admin/batches/batch-a**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith("/members") && route.request().method() === "GET") {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ members: [], currentPage: 0, totalPages: 0, totalItems: 0 }),
      });
      return;
    }
    if (url.pathname.endsWith("/assignments")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ assignments: [], currentPage: 0, totalPages: 0, totalItems: 0 }),
      });
      return;
    }
    if (url.pathname.endsWith("/report")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          summary: { studentCount: 0, assignedProblemCount: 0, completedProblemCount: 0, completionPercent: "0", solvedCells: 0, attemptedCells: 0, unsolvedCells: 0, submissionCount: 0, acceptedSubmissionCount: 0 },
          problems: [],
          students: [],
          recentActivity: [],
          totalPages: 0,
        }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: "batch-a",
        name: "Batch A",
        description: "North section",
        active: true,
        memberCount: 0,
        activeMemberCount: 0,
        assignmentCount: 0,
        viewerRole: "ADMIN",
      }),
    });
  });
  await page.goto("/admin/batches/batch-a");
  await expect(page.getByRole("heading", { name: "Batch A" })).toBeVisible();
  await page.getByRole("button", { name: "Manage batch" }).click();
  await expect(page.getByRole("menuitem", { name: "Archive batch" })).toBeVisible();
  await page.getByRole("menuitem", { name: "Add students" }).click();
  await page.getByLabel("Search registered students").fill("ada");
  await expect(page.getByText("Eligible to add")).toBeVisible();
});

test("coordinator sees only assigned batch actions", async ({ page }) => {
  await signIn(page, { username: "coord", role: "USER", id: "coord-1" });
  await page.route("**/api/my/batches**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        batches: [{ batchId: "batch-a", name: "Batch A", description: "North", active: true, institution: "North Institute" }],
      }),
    });
  });
  await page.route("**/api/admin/batches/batch-a**", async (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith("/members") || url.pathname.endsWith("/assignments") || url.pathname.endsWith("/report")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ members: [], assignments: [], students: [], problems: [], summary: {}, recentActivity: [], totalPages: 0 }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: "batch-a",
        name: "Batch A",
        description: "North section",
        active: true,
        memberCount: 0,
        activeMemberCount: 0,
        assignmentCount: 0,
        viewerRole: "COORDINATOR",
      }),
    });
  });
  await page.goto("/my/batches");
  await expect(page.getByRole("link", { name: "Batch A" })).toBeVisible();
  await page.getByRole("link", { name: "Batch A" }).click();
  await expect(page.getByRole("heading", { name: "Batch A" })).toBeVisible();
  await page.getByRole("button", { name: "Manage batch" }).click();
  await expect(page.getByRole("menuitem", { name: "Add students" })).toBeVisible();
  await expect(page.getByRole("menuitem", { name: "Archive batch" })).toHaveCount(0);
  await expect(page.getByRole("menuitem", { name: "Manage coordinators" })).toHaveCount(0);
});
