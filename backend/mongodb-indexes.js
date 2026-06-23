/**
 * MongoDB Index Creation Script for CoderzClub
 *
 * Run this script in the MongoDB shell to create or verify production-ready indexes.
 * This script is idempotent and avoids duplicate indexes when Spring auto-index
 * creation is enabled.
 *
 * Usage:
 *   mongo "your-connection-string"
 *   use your_database_name
 *   load('mongodb-indexes.js')
 */

function ensureIndex(coll, keys, options) {
  options = options || {};
  var existing = coll.getIndexes().some(function(idx) {
    return JSON.stringify(idx.key) === JSON.stringify(keys);
  });
  if (!existing) {
    print('Creating index ' + (options.name || JSON.stringify(keys)) + ' on ' + coll.getName());
    coll.createIndex(keys, options);
  } else {
    print('Index already exists on ' + coll.getName() + ': ' + (options.name || JSON.stringify(keys)));
  }
}

// ===== PROBLEMS COLLECTION INDEXES =====
// Search and filtering by difficulty, category, premium flag, tags, and numeric ID.
ensureIndex(db.problems, { "difficulty": 1, "category": 1 }, { name: "difficulty_category_idx" });
ensureIndex(db.problems, { "tags": 1 }, { name: "tags_idx" });
ensureIndex(db.problems, { "isPremium": 1, "difficulty": 1 }, { name: "isPremium_difficulty_idx" });
ensureIndex(db.problems, { "numericId": 1 }, { name: "numericId_idx" });
ensureIndex(db.problems, { "title": "text", "tags": "text", "category": "text" }, { name: "title_tags_category_text_idx" });

// Existing heuristic indexes for queries and filtering.
ensureIndex(db.problems, { "title": 1 }, { name: "title_idx" });
ensureIndex(db.problems, { "difficulty": 1 }, { name: "difficulty_idx" });
ensureIndex(db.problems, { "difficulty": 1, "tags": 1 }, { name: "difficulty_tags_idx" });

// ===== USERS COLLECTION INDEXES =====
// Leaderboard, role lookup, streak ordering, and email verification filtering.
ensureIndex(db.users, { "totalPoints": -1 }, { name: "totalPoints_desc_idx" });
ensureIndex(db.users, { "role": 1 }, { name: "role_idx" });
ensureIndex(db.users, { "currentStreak": -1 }, { name: "currentStreak_desc_idx" });
ensureIndex(db.users, { "emailVerified": 1 }, { name: "emailVerified_idx" });

// Unique username/email indexes are created by Spring @Indexed(unique=true) in User model.

// ===== SUBMISSIONS COLLECTION INDEXES =====
// Recent history, per-user/problem counts, and result-based filtering.
ensureIndex(db.submissions, { "userId": 1, "createdAt": -1 }, { name: "userId_createdAt_desc_idx" });
ensureIndex(db.submissions, { "userId": 1, "problemId": 1, "createdAt": -1 }, { name: "userId_problemId_createdAt_desc_idx" });
ensureIndex(db.submissions, { "userId": 1, "result": 1, "createdAt": -1 }, { name: "userId_result_createdAt_desc_idx" });
ensureIndex(db.submissions, { "problemId": 1, "result": 1, "createdAt": -1 }, { name: "problemId_result_createdAt_desc_idx" });
ensureIndex(db.submissions, { "userId": 1, "problemId": 1, "result": 1 }, { name: "userId_problemId_result_idx" });

// Existing supporting indexes.
ensureIndex(db.submissions, { "userId": 1 }, { name: "userId_idx" });
ensureIndex(db.submissions, { "problemId": 1 }, { name: "problemId_idx" });
ensureIndex(db.submissions, { "result": 1 }, { name: "result_idx" });
ensureIndex(db.submissions, { "createdAt": -1 }, { name: "createdAt_desc_idx" });

// ===== SUBMISSION_JOBS COLLECTION INDEXES =====
// Queue scanning and recovery for pending/locked jobs.
ensureIndex(db.submission_jobs, { "status": 1, "createdAt": 1 }, { name: "status_createdAt_idx" });
ensureIndex(db.submission_jobs, { "userId": 1, "status": 1, "createdAt": -1 }, { name: "userId_status_createdAt_desc_idx" });
ensureIndex(db.submission_jobs, { "lockedUntil": 1 }, { name: "lockedUntil_idx" });
ensureIndex(db.submission_jobs, { "status": 1, "lockedUntil": 1 }, { name: "status_lockedUntil_idx" });

// Existing supporting indexes.
ensureIndex(db.submission_jobs, { "submissionId": 1 }, { name: "submissionId_idx" });
ensureIndex(db.submission_jobs, { "status": 1 }, { name: "status_idx" });
ensureIndex(db.submission_jobs, { "createdAt": -1 }, { name: "createdAt_desc_idx" });

// ===== PROBLEM_BUNDLES COLLECTION INDEXES =====
ensureIndex(db.problem_bundles, { "name": 1 }, { name: "name_idx" });
ensureIndex(db.problem_bundles, { "difficulty": 1 }, { name: "difficulty_idx" });
ensureIndex(db.problem_bundles, { "createdAt": -1 }, { name: "createdAt_desc_idx" });

// ===== SUBSCRIPTIONS COLLECTION INDEXES =====
ensureIndex(db.subscriptions, { "userId": 1 }, { name: "userId_idx" });
ensureIndex(db.subscriptions, { "status": 1 }, { name: "status_idx" });
ensureIndex(db.subscriptions, { "expiryDate": 1 }, { name: "expiryDate_idx" });

print('Index creation script completed. Verify indexes with db.collection.getIndexes().');
