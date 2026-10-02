import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const root = dirname(fileURLToPath(import.meta.url));

test("privacy and terms routes exist and avoid compliance claims", () => {
  const app = readFileSync(join(root, "../App.jsx"), "utf8");
  assert.match(app, /path="\/privacy"/);
  assert.match(app, /path="\/terms"/);
  assert.match(app, /<SiteFooter \/>/);
  const privacy = readFileSync(join(root, "../Components/PrivacyPage.jsx"), "utf8");
  const terms = readFileSync(join(root, "../Components/TermsPage.jsx"), "utf8");
  assert.doesNotMatch(privacy, /GDPR compliant/i);
  assert.doesNotMatch(terms, /GDPR compliant/i);
  assert.doesNotMatch(privacy, /Requires product\/legal review/);
  assert.doesNotMatch(terms, /Requires product\/legal review/);
  assert.match(privacy, /not a complete legal privacy policy/i);
  assert.match(terms, /not a complete legal agreement/i);
});
