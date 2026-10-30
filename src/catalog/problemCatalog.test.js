import test from "node:test";
import assert from "node:assert/strict";
import { catalogStatus, filtersAreActive } from "./problemCatalog.js";

test("loading is distinct from an empty catalog", () => {
  assert.equal(catalogStatus({ loading: true, count: 0 }).kind, "loading");
  assert.equal(catalogStatus({ count: 0 }).kind, "empty");
});

test("filters produce a no-results state", () => {
  assert.equal(filtersAreActive({ search: "Reverse" }), true);
  assert.equal(catalogStatus({ count: 0, filtersActive: true }).kind, "no-results");
});

test("offline is not described as a server failure", () => {
  const status = catalogStatus({ error: "offline", offline: true, count: 0 });
  assert.equal(status.kind, "offline");
  assert.match(status.text, /offline/i);
});

test("a failed request can be retried", () => {
  const status = catalogStatus({ error: "error", count: 0 });
  assert.equal(status.kind, "error");
  assert.match(status.text, /could not be loaded/i);
});
