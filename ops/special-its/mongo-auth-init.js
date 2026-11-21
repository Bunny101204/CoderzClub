// Runs once on empty volume for coderzclub-special-mongo-auth.
// IndexPrivilegeIT needs an app user that can read/write but cannot createIndex.

db = db.getSiblingDB("coderzclub_idx");

db.createRole({
  role: "readWriteNoIndex",
  privileges: [
    {
      resource: { db: "coderzclub_idx", collection: "" },
      actions: [
        "find",
        "insert",
        "update",
        "remove",
        "listIndexes",
        "listCollections",
        "collStats",
        "dbStats"
      ]
    }
  ],
  roles: []
});

db.createUser({
  user: "noindex",
  pwd: "noindex-local-only",
  roles: [
    { role: "readWriteNoIndex", db: "coderzclub_idx" }
  ]
});
