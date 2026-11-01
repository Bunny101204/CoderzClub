const { test } = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const source = fs.readFileSync(__dirname + "/verify-submission-mongo.js", "utf8");
const expected = [
 ["mutation_idempotency", "idempotency_operation_unique", {userId:1,scope:1,operationKey:1}, true],
 ["mutation_idempotency", "idempotency_open_subject_unique", {userId:1,scope:1,subjectId:1}, true, {open:true}],
 ["mutation_idempotency", "mutation_idempotency_ttl", {expiresAt:1}, false, undefined, 0],
 ["submission_jobs", "submission_job_idempotency_unique", {userId:1,idempotencyScope:1,idempotencyKey:1}, true, {idempotencyKey:{$exists:true}}],
 ["submission_outbox", "aggregate_event_unique_idx", {aggregateId:1,eventType:1}, true],
 ["submissions", "submissionJobId_unique_sparse_idx", {submissionJobId:1}, true, {submissionJobId:{$type:"string"}}],
 ["user_solved_problems", "existing_compatible_name", {userId:1,problemId:1}, true],
 ["submission_test_results", "job_testcase_attempt_index", {jobId:1,testcaseIndex:1,attemptCount:1}, true],
    ["contest_problem_snapshots", "contestId_problemId_unique_idx", { contestId: 1, problemId: 1 }, true],
    ["contest_problem_solves", "contestId_userId_problemId_unique_idx", { contestId: 1, userId: 1, problemId: 1 }, true],
    ["contest_scores", "contestId_userId_unique_idx", { contestId: 1, userId: 1 }, true],
    ["code_drafts", "userId_scopeKey_problemId_languageId_unique_idx", { userId: 1, scopeKey: 1, problemId: 1, languageId: 1 }, true],
];
function fixture() {
 const indexes = {};
 for (const [c,name,key,unique,partialFilterExpression,expireAfterSeconds] of expected) {
  (indexes[c] ||= []).push({name,key,unique,partialFilterExpression,expireAfterSeconds});
 }
 let reads=0, aborts=0;
 const db = {
  runCommand(c) {
   if (c.hello) return {ok:1,setName:"audit",isWritablePrimary:true,logicalSessionTimeoutMinutes:30};
   if (c.connectionStatus) return {ok:1,authInfo:{}};
   throw Error("unexpected command");
  },
  getName: () => "audit",
  getCollectionInfos: () => [...Object.keys(indexes),"users","problems"].map(name=>({name})),
  getCollection: c => ({getIndexes:()=>indexes[c]}),
  getMongo: () => ({startSession:()=>({
   startTransaction(){}, getDatabase:()=>({getCollection:()=>({findOne(){reads++;return null;}})}),
   abortTransaction(){aborts++;},endSession(){}
  })}),
 };
 return {db,indexes,run(){vm.runInNewContext(source,{db,print(){}});return {reads,aborts};}};
}
test("valid definitions and read-only snapshot transaction",()=>assert.deepEqual(fixture().run(),{reads:1,aborts:1}));
test("missing unique index fails",()=>{const f=fixture();f.indexes.submission_jobs=[];assert.throws(()=>f.run(),/preflight failed/);});
test("nonunique index fails",()=>{const f=fixture();f.indexes.submission_outbox[0].unique=false;assert.throws(()=>f.run(),/preflight failed/);});
test("wrong partial filter fails",()=>{const f=fixture();f.indexes.submissions[0].partialFilterExpression={submissionJobId:{$exists:true}};assert.throws(()=>f.run(),/preflight failed/);});
test("wrong TTL value fails",()=>{const f=fixture();f.indexes.mutation_idempotency[2].expireAfterSeconds=3600;assert.throws(()=>f.run(),/preflight failed/);});
test("hidden critical index fails",()=>{const f=fixture();f.indexes.submission_jobs[0].hidden=true;assert.throws(()=>f.run(),/preflight failed/);});
test("mongos topology fails current implementation requirement",()=>{const f=fixture();f.db.runCommand=c=>c.hello?{ok:1,msg:"isdbgrid",isWritablePrimary:true,logicalSessionTimeoutMinutes:30}:{ok:1};assert.throws(()=>f.run(),/preflight failed/);});
test("wrong critical index name fails",()=>{const f=fixture();f.indexes.mutation_idempotency[0].name="other";assert.throws(()=>f.run(),/preflight failed/);});

test("missing contest reward deduplication index fails",()=>{const f=fixture();f.indexes.contest_problem_solves=[];assert.throws(()=>f.run(),/preflight failed/);});
