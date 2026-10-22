import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const form = readFileSync(join(root, "../Components/admin/contests/ContestForm.jsx"), "utf8");
const selector = readFileSync(join(root, "../Components/admin/contests/ContestProblemSelector.jsx"), "utf8");
const list = readFileSync(join(root, "../Components/admin/contests/ContestAdminList.jsx"), "utf8");
const authPage = readFileSync(join(root, "../Components/AuthPage.jsx"), "utf8");
const apiClient = readFileSync(join(root, "../apiClient.js"), "utf8");

test("contest problem search can close without hiding the selected list", () => {
  assert.match(selector, /Add problems/);
  assert.match(selector, />\s*Done\s*</);
  assert.match(selector, /pickerOpen &&/);
  assert.match(selector, /Move up/);
  assert.match(selector, /Remove/);
  assert.match(form, /Save draft stores an unpublished contest/);
  assert.match(form, /Use Publish when it is ready/);
  assert.doesNotMatch(form, /adminContests\.publish\([\s\S]{0,80}adminContests\.create/);
});

test("saved drafts stay in the admin list and publish errors stay specific", () => {
  assert.match(list, /Unpublished drafts appear in this list/);
  assert.match(list, /Could not publish this contest/);
  assert.match(form, /Could not publish this contest/);
});

test("public registration no longer offers an admin role", () => {
  assert.doesNotMatch(authPage, /Register as Admin/);
  assert.doesNotMatch(authPage, /registerAsAdmin/);
  assert.match(authPage, /api\.auth\.confirmEmail/);
  assert.doesNotMatch(authPage, /window\.location\.href = `\/api\/confirm-email/);
});

test("the API client retries through the idempotent-read guard", () => {
  assert.match(apiClient, /shouldRetryTransientFailure/);
  assert.doesNotMatch(apiClient, /error\.response\?\.status >= 500/);
});
