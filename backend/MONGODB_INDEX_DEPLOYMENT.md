# MongoDB Index Deployment

Run the index script after the `numericId` backfill has completed and before exposing problem listing to high traffic. Index creation is idempotent, but the unique solved-record index must not be applied while duplicate `(userId, problemId)` records exist.

Apply indexes with `mongosh`:

```sh
mongosh "mongodb+srv://<user>:<password>@<cluster>/<database>?retryWrites=true&w=majority" backend/mongodb-indexes.js
```

Run this after the numeric ID backfill and duplicate cleanup. The command exits
non-zero if the unique `numericId` index or another critical index is missing.
For CI/deployment validation, run the same command with `--quiet` and fail on
its exit code:

```sh
mongosh --quiet "$MONGODB_ATLAS_URI" backend/mongodb-indexes.js
```

Verify the problem and solved-record indexes:

```sh
mongosh "mongodb+srv://<user>:<password>@<cluster>/<database>?retryWrites=true&w=majority" --eval 'db.problems.getIndexes(); db.user_solved_problems.getIndexes()'
```

Safe critical-index verification without changing data:

```sh
mongosh --quiet "$MONGODB_ATLAS_URI" --eval '
	const required = [
		["problems", "numericId_id_idx"],
		["problems", "numericId_unique_sparse_idx"],
		["submissions", "userId_createdAt_desc_idx"],
		["submission_jobs", "status_lockedUntil_idx"],
		["user_solved_problems", "userId_problemId_unique_idx"],
		["submission_outbox", "aggregate_event_unique_idx"],
		["submission_outbox", "status_nextAttempt_locked_idx"]
	];
	for (const [collection, name] of required) {
		if (collection === "user_solved_problems") {
			if (!db[collection].getIndexes().some(index =>
					index.key.userId === 1 && index.key.problemId === 1 && index.unique === true)) {
				throw new Error("Missing unique user/problem index");
			}
		} else if (!db[collection].getIndexes().some(index => index.name === name)) {
			throw new Error(`Missing critical index ${collection}.${name}`);
		}
	}
	print("Critical indexes verified");
'
```

MongoDB should have the `numericId_id_idx` ordering index and the unique sparse
`numericId_unique_sparse_idx` before high-traffic problem requests are enabled.
New IDs are allocated through the atomic `counters` document
`_id=problems.numericId`.

## Explain-plan checks

Run these against a representative Atlas dataset and confirm the winning plan
uses an index rather than `COLLSCAN`:

```javascript
db.problems.find({ difficulty: "EASY", numericId: { $gt: 100 } })
	.sort({ numericId: 1, _id: 1 }).limit(20).explain("executionStats");
db.submissions.find({ userId: "user-1" }).sort({ createdAt: -1 })
	.limit(20).explain("executionStats");
db.submission_jobs.find({ status: "PENDING", lockedUntil: { $lte: new Date() } })
	.sort({ createdAt: 1 }).limit(20).explain("executionStats");
db.submissions.countDocuments({ userId: "user-1", createdAt: { $gte: ISODate() } });
db.submissions.countDocuments({ userId: "user-1", problemId: "problem-1", createdAt: { $gte: ISODate() } });
```

The last two checks correspond to the MongoDB fallback used by the Redis rate
limiter. Use a concrete start-of-day `ISODate` value when running them.