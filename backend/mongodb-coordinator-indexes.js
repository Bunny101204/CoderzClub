/**
 * Coordinator indexes only. Does not create, drop, or alter any other index.
 *
 * Do not run this against production until the release is approved.
 * Do not substitute backend/mongodb-indexes.js for this step: that file also
 * manages contest, submission, and contest-run indexes.
 *
 *   mongosh "$MONGODB_URI" backend/mongodb-coordinator-indexes.js
 *
 * The script aborts before createIndex when batch_coordinators already has
 * duplicate {batchId, userId} pairs or an index on those keys that is not unique.
 */
var collectionName = "batch_coordinators";
var uniqueKeys = { batchId: 1, userId: 1 };
var uniqueName = "batchId_userId_unique_idx";
var lookupKeys = { userId: 1, batchId: 1 };
var lookupName = "userId_batchId_idx";

function sameKeys(index, keys) {
  return JSON.stringify(index.key) === JSON.stringify(keys);
}

function requireCompatible(coll, keys, name, unique) {
  var matches = coll.getIndexes().filter(function(index) { return sameKeys(index, keys); });
  if (matches.length === 0) return null;
  var match = matches[0];
  if (unique && match.unique !== true) {
    throw new Error(collectionName + " already has non-unique index " + match.name
      + " on " + JSON.stringify(keys) + ". Resolve it before creating " + name + ".");
  }
  if (match.name !== name) {
    print("Using existing index " + match.name + " for " + JSON.stringify(keys)
      + " instead of creating a second index named " + name + ".");
  }
  return match;
}

var names = db.getCollectionNames();
var coll = db.getCollection(collectionName);
if (names.indexOf(collectionName) === -1) {
  print(collectionName + " does not exist yet. Duplicate check is empty.");
  print("Creating unique index " + uniqueName);
  coll.createIndex(uniqueKeys, { name: uniqueName, unique: true });
  print("Creating index " + lookupName);
  coll.createIndex(lookupKeys, { name: lookupName });
} else {
  var duplicates = coll.aggregate([
    { $group: { _id: { batchId: "$batchId", userId: "$userId" }, count: { $sum: 1 } } },
    { $match: { count: { $gt: 1 } } },
    { $limit: 20 }
  ]).toArray();
  if (duplicates.length > 0) {
    duplicates.forEach(function(row) {
      print("Duplicate coordinator key batchId=" + row._id.batchId + " userId=" + row._id.userId + " count=" + row.count);
    });
    throw new Error("Refusing to create the unique coordinator index while duplicate memberships exist.");
  }
  print("No duplicate coordinator keys found.");
  var uniqueIndex = requireCompatible(coll, uniqueKeys, uniqueName, true);
  if (!uniqueIndex) {
    print("Creating unique index " + uniqueName);
    coll.createIndex(uniqueKeys, { name: uniqueName, unique: true });
  }
  var lookupIndex = requireCompatible(coll, lookupKeys, lookupName, false);
  if (!lookupIndex) {
    print("Creating index " + lookupName);
    coll.createIndex(lookupKeys, { name: lookupName });
  }
}

["batchId_userId_unique_idx", "userId_batchId_idx"].forEach(function(name) {
  var index = coll.getIndexes().find(function(item) { return item.name === name; });
  if (!index) throw new Error("Coordinator index missing after creation: " + name);
  print("Verified " + collectionName + "." + name + " keys=" + JSON.stringify(index.key)
    + " unique=" + (index.unique === true));
});
if (coll.getIndexes().some(function(index) {
  return sameKeys(index, uniqueKeys) && index.unique !== true;
})) {
  throw new Error("Unique coordinator key is still covered by a non-unique index.");
}
print("Coordinator index preparation completed.");
