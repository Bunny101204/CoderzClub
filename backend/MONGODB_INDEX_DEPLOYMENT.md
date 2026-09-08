# MongoDB Index Deployment

Run the index script after the `numericId` backfill has completed and before exposing problem listing to high traffic. Index creation is idempotent, but the unique solved-record index must not be applied while duplicate `(userId, problemId)` records exist.

Apply indexes with `mongosh`:

```sh
mongosh "mongodb+srv://<user>:<password>@<cluster>/<database>?retryWrites=true&w=majority" mongodb-indexes.js
```

Verify the problem and solved-record indexes:

```sh
mongosh "mongodb+srv://<user>:<password>@<cluster>/<database>?retryWrites=true&w=majority" --eval 'db.problems.getIndexes(); db.user_solved_problems.getIndexes()'
```

MongoDB should have the `numericId_id_idx` ordering index before high-traffic paginated problem requests are enabled. New and legacy problems receive `numericId` through the application write path and startup backfill.