import { mkdirSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

export class ResourceLedger {
  constructor(runId) {
    this.runId = runId;
    this.entries = [];
    this.path = resolve("artifacts", "playwright-production", runId, "resource-ledger.json");
    mkdirSync(resolve("artifacts", "playwright-production", runId), { recursive: true });
    this.flush();
  }

  record(type, id, name) {
    if (!type || !id) throw new Error("Cannot record a created resource without its type and id.");
    this.entries.push({ type, id, name, createdByRun: this.runId });
    this.flush();
  }

  flush() {
    writeFileSync(this.path, JSON.stringify({
      runId: this.runId,
      createdOnly: true,
      cleanupPolicy: "No automatic deletion. Delete only listed resources after separate owner approval.",
      resources: this.entries,
    }, null, 2));
  }

  snapshot() {
    return {
      runId: this.runId,
      createdOnly: true,
      resources: [...this.entries],
    };
  }
}
