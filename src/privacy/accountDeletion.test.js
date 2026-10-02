import test from "node:test";
import assert from "node:assert/strict";
import { canConfirmAccountDeletion, clearLocalAuthState, DELETE_CONFIRMATION_PHRASE } from "./accountDeletion.js";

test("deletion requires the exact confirmation phrase", () => {
  assert.equal(DELETE_CONFIRMATION_PHRASE, "DELETE");
  assert.equal(canConfirmAccountDeletion("DELETE"), true);
  assert.equal(canConfirmAccountDeletion(" DELETE "), true);
  assert.equal(canConfirmAccountDeletion("delete"), false);
  assert.equal(canConfirmAccountDeletion(""), false);
});

test("successful deletion clears stored tokens", () => {
  globalThis.localStorage = {
    store: { jwtToken: "abc", token: "def" },
    removeItem(key) { delete this.store[key]; },
    getItem(key) { return this.store[key]; }
  };
  globalThis.sessionStorage = {
    store: { jwtToken: "ghi", token: "jkl" },
    removeItem(key) { delete this.store[key]; },
    getItem(key) { return this.store[key]; }
  };
  clearLocalAuthState();
  assert.equal(globalThis.localStorage.getItem("jwtToken"), undefined);
  assert.equal(globalThis.localStorage.getItem("token"), undefined);
  assert.equal(globalThis.sessionStorage.getItem("jwtToken"), undefined);
  assert.equal(globalThis.sessionStorage.getItem("token"), undefined);
});
