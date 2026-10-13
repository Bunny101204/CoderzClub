import test from "node:test";
import assert from "node:assert/strict";
import { prefixApiPath, resolveApiBaseUrl } from "./apiBase.js";

test("development ignores the production API base and keeps the Vite proxy", () => {
  assert.equal(resolveApiBaseUrl({ dev: true, raw: "http://localhost:8080" }), "");
  assert.equal(prefixApiPath("/api/problems", ""), "/api/problems");
});

test("production accepts an HTTPS API origin and strips a trailing slash", () => {
  assert.equal(
    resolveApiBaseUrl({ dev: false, raw: "https://api.coderzclub.example/" }),
    "https://api.coderzclub.example"
  );
  assert.equal(
    prefixApiPath("/api/problems/1", "https://api.coderzclub.example"),
    "https://api.coderzclub.example/api/problems/1"
  );
});

test("production rejects missing, HTTP, localhost, development ports, and Codespaces tunnels", () => {
  assert.throws(() => resolveApiBaseUrl({ dev: false, raw: "" }), /required/);
  assert.throws(() => resolveApiBaseUrl({ dev: false, raw: "http://api.coderzclub.example" }), /HTTPS/);
  assert.throws(() => resolveApiBaseUrl({ dev: false, raw: "https://localhost:8080" }), /local development host/);
  assert.throws(() => resolveApiBaseUrl({ dev: false, raw: "https://127.0.0.1:5173" }), /local development host/);
  assert.throws(() => resolveApiBaseUrl({ dev: false, raw: "https://api.coderzclub.example:8080" }), /development port/);
  assert.throws(() => resolveApiBaseUrl({ dev: false, raw: "https://api.coderzclub.example:5173" }), /development port/);
  assert.throws(
    () => resolveApiBaseUrl({ dev: false, raw: "https://my-codespace-8080.app.github.dev" }),
    /Codespaces/
  );
});

test("non-API and already absolute URLs are not rewritten", () => {
  const base = "https://api.coderzclub.example";
  assert.equal(prefixApiPath("/assets/index.js", base), "/assets/index.js");
  assert.equal(prefixApiPath("https://other.example/api/problems", base), "https://other.example/api/problems");
});
