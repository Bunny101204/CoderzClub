import test from "node:test";
import assert from "node:assert/strict";
import { batchTabLabel } from "./adminBatchLabel.js";

test("an unloaded batch list does not claim a count of zero", () => {
  assert.equal(batchTabLabel(null), "Batches");
  assert.equal(batchTabLabel(undefined), "Batches");
});

test("a loaded batch list shows the real count", () => {
  assert.equal(batchTabLabel([]), "Batches (0)");
  assert.equal(batchTabLabel([{ id: "a" }, { id: "b" }]), "Batches (2)");
});
