/**
 * MongoDB Index Creation Script for CoderzClub
 * 
 * Run this script in MongoDB shell to create indexes for frequently queried fields.
 * This will improve query performance by 10-100x depending on collection size.
 * 
 * To run:
 * 1. Connect to MongoDB: mongo "your-connection-string"
 * 2. Select database: use your_database_name
 * 3. Copy and paste the commands below
 */

// ===== PROBLEMS COLLECTION INDEXES =====
// Indexes for search and filtering
db.problems.createIndex({ "title": 1 }, { name: "title_index" });
db.problems.createIndex({ "difficulty": 1 }, { name: "difficulty_index" });
db.problems.createIndex({ "tags": 1 }, { name: "tags_index" });
db.problems.createIndex({ "_id": 1 }, { unique: true, name: "id_index_unique" });

// Compound index for common search patterns
db.problems.createIndex({ "title": 1, "difficulty": 1 }, { name: "title_difficulty_index" });
db.problems.createIndex({ "difficulty": 1, "tags": 1 }, { name: "difficulty_tags_index" });

// ===== USERS COLLECTION INDEXES =====
db.users.createIndex({ "_id": 1 }, { unique: true, name: "id_index_unique" });
db.users.createIndex({ "email": 1 }, { unique: true, name: "email_index_unique" });
db.users.createIndex({ "username": 1 }, { unique: true, name: "username_index_unique" });
db.users.createIndex({ "role": 1 }, { name: "role_index" });
db.users.createIndex({ "lastActiveDate": 1 }, { name: "lastActiveDate_index" });

// ===== SUBMISSIONS COLLECTION INDEXES =====
db.submissions.createIndex({ "_id": 1 }, { unique: true, name: "id_index_unique" });
db.submissions.createIndex({ "userId": 1 }, { name: "userId_index" });
db.submissions.createIndex({ "problemId": 1 }, { name: "problemId_index" });
db.submissions.createIndex({ "result": 1 }, { name: "result_index" });
db.submissions.createIndex({ "language": 1 }, { name: "language_index" });
db.submissions.createIndex({ "createdAt": -1 }, { name: "createdAt_descending_index" });

// Compound indexes for common query patterns
db.submissions.createIndex({ "userId": 1, "problemId": 1 }, { name: "userId_problemId_index" });
db.submissions.createIndex({ "userId": 1, "result": 1 }, { name: "userId_result_index" });
db.submissions.createIndex({ "problemId": 1, "result": 1 }, { name: "problemId_result_index" });

// ===== SUBMISSION_JOBS COLLECTION INDEXES =====
db.submission_jobs.createIndex({ "_id": 1 }, { unique: true, name: "id_index_unique" });
db.submission_jobs.createIndex({ "userId": 1 }, { name: "userId_index" });
db.submission_jobs.createIndex({ "submissionId": 1 }, { name: "submissionId_index" });
db.submission_jobs.createIndex({ "status": 1 }, { name: "status_index" });
db.submission_jobs.createIndex({ "createdAt": -1 }, { name: "createdAt_descending_index" });

// ===== PROBLEM_BUNDLES COLLECTION INDEXES =====
db.problem_bundles.createIndex({ "_id": 1 }, { unique: true, name: "id_index_unique" });
db.problem_bundles.createIndex({ "name": 1 }, { name: "name_index" });
db.problem_bundles.createIndex({ "difficulty": 1 }, { name: "difficulty_index" });
db.problem_bundles.createIndex({ "createdAt": -1 }, { name: "createdAt_descending_index" });

// ===== SUBSCRIPTIONS COLLECTION INDEXES =====
db.subscriptions.createIndex({ "_id": 1 }, { unique: true, name: "id_index_unique" });
db.subscriptions.createIndex({ "userId": 1 }, { name: "userId_index" });
db.subscriptions.createIndex({ "status": 1 }, { name: "status_index" });
db.subscriptions.createIndex({ "expiryDate": 1 }, { name: "expiryDate_index" });

// ===== VERIFY INDEXES =====
// Run these commands to verify indexes were created successfully
db.problems.getIndexes();
db.users.getIndexes();
db.submissions.getIndexes();
db.submission_jobs.getIndexes();
db.problem_bundles.getIndexes();
db.subscriptions.getIndexes();

// ===== INDEX STATISTICS =====
// To get index usage statistics (MongoDB 4.4+):
db.problems.aggregate([{ $indexStats: {} }]);
db.users.aggregate([{ $indexStats: {} }]);
db.submissions.aggregate([{ $indexStats: {} }]);

// ===== DROP INDEXES (if needed for maintenance) =====
// db.problems.dropIndex("title_index");
// db.users.dropIndex("email_index_unique");
// db.submissions.dropIndex("userId_problemId_index");

// ===== PERFORMANCE NOTES =====
// After creating indexes:
// - Query performance should improve 10-100x depending on collection size
// - Index creation may take 1-5 minutes depending on collection size
// - Large collections (>100GB) should be indexed during low-traffic periods
// - Monitor index size: db.collection.stats() includes "indexSizes"
// - Indexes consume disk space (~10-30% of collection size)
