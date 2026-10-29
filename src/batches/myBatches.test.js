import test from "node:test";
import assert from "node:assert/strict";
import { myBatchesView, practiceProblemLabel, practiceProblemPath } from "./myBatches.js";

test("active student sees an enrolled batch and not coordinator wording", () => {
  const view = myBatchesView({
    batches: [],
    enrolled: {
      items: [{ batchId: "b1", name: "Batch 1", assignmentCount: 2, problems: [{ problemId: "p1", numericId: 7, title: "Two Sum" }] }],
      currentPage: 0,
      totalPages: 1,
      totalItems: 1,
    },
  });
  assert.equal(view.empty, false);
  assert.equal(view.showEnrolledSection, true);
  assert.equal(view.showCoordinatorSection, false);
  assert.equal(view.enrolled[0].name, "Batch 1");
  assert.equal(practiceProblemLabel(view.enrolled[0].problems[0]), "7. Two Sum");
  assert.equal(practiceProblemPath(view.enrolled[0].problems[0]), "/problem/p1");
});

test("student without memberships gets an empty state", () => {
  const view = myBatchesView({ batches: [], enrolled: { items: [], totalItems: 0, totalPages: 0, currentPage: 0 } });
  assert.equal(view.empty, true);
  assert.equal(view.showCoordinatorSection, false);
  assert.equal(view.enrolled.length, 0);
});

test("coordinator sees coordinated batches separately from enrollments", () => {
  const view = myBatchesView({
    batches: [{ batchId: "b2", name: "Managed" }],
    enrolled: { items: [], totalItems: 0 },
  });
  assert.equal(view.showCoordinatorSection, true);
  assert.equal(view.showEnrolledSection, false);
  assert.equal(view.coordinated[0].batchId, "b2");
});

test("a member who also coordinates keeps both sections", () => {
  const view = myBatchesView({
    batches: [{ batchId: "b1", name: "Batch 1" }],
    enrolled: { items: [{ batchId: "b1", name: "Batch 1" }], currentPage: 0, totalPages: 1 },
  });
  assert.equal(view.showEnrolledSection, true);
  assert.equal(view.showCoordinatorSection, true);
  assert.equal(view.enrolled[0].batchId, "b1");
  assert.equal(view.coordinated[0].batchId, "b1");
});
