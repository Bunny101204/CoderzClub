/**
 * Read-only check for the two batch coordinator indexes.
 * Exit 0: both indexes are present and the membership index is unique.
 * Exit 10: collection or indexes are absent, so the apply script may create them.
 * Exit 1: duplicates or a conflicting non-unique index. Do not create indexes.
 *
 *   mongosh "$MONGODB_URI" --quiet backend/mongodb-coordinator-indexes-check.js
 */
var collectionName = "batch_coordinators";
var uniqueKeys = { batchId: 1, userId: 1 };
var lookupKeys = { userId: 1, batchId: 1 };

function sameKeys(index, keys) {
  return JSON.stringify(index.key) === JSON.stringify(keys);
}

function finish(code, message) {
  print(message);
  quit(code);
}

if (db.getCollectionNames().indexOf(collectionName) === -1) {
  finish(10, collectionName + " is absent");
}

var coll = db.getCollection(collectionName);
var duplicates = coll.aggregate([
  { $group: { _id: { batchId: "$batchId", userId: "$userId" }, count: { $sum: 1 } } },
  { $match: { count: { $gt: 1 } } },
  { $limit: 20 }
]).toArray();
if (duplicates.length > 0) {
  duplicates.forEach(function(row) {
    print("Duplicate coordinator key batchId=" + row._id.batchId + " userId=" + row._id.userId + " count=" + row.count);
  });
  finish(1, "duplicate coordinator keys exist");
}

var indexes = coll.getIndexes();
var unique = indexes.find(function(index) { return sameKeys(index, uniqueKeys); });
var lookup = indexes.find(function(index) { return sameKeys(index, lookupKeys); });
if (unique && unique.unique !== true) {
  finish(1, "conflicting non-unique index " + unique.name + " on {batchId:1,userId:1}");
}
if (!unique || !lookup) {
  finish(10, "coordinator indexes are incomplete");
}
print("Verified " + unique.name + " unique=true");
print("Verified " + lookup.name);
finish(0, "coordinator indexes are ready");
