import test from "node:test";
import assert from "node:assert/strict";
import { submissionErrorMessage } from "./submissionError.js";

test("database details never become admission messages, including legacy HTTP 400", () => {
  for (const status of [400, 401, 403, 404, 409, 422, 429, 500, 503, undefined]) {
    const message = submissionErrorMessage({ response: { status, data: {
      error: "MongoDB OperationNotSupportedInTransaction code 263 cluster.internal:27017",
    } }, message: "database password" });
    assert.doesNotMatch(message, /Mongo|263|cluster|password/);
    assert.ok(message.length > 10);
  }
});
