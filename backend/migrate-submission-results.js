// Run once with mongosh against the application database.
// This migrates legacy embedded results, then removes large arrays from jobs.
db.submission_jobs.find({ testResults: { $exists: true, $ne: [] } }).forEach(function (job) {
  var publicCount = job.publicTestCases ? job.publicTestCases.length : 0;
  (job.testResults || []).forEach(function (result, index) {
    var isPublic = index < publicCount;
    db.submission_test_results.updateOne(
      { jobId: job._id, testcaseIndex: index },
      { $setOnInsert: {
        jobId: job._id,
        testcaseIndex: index,
        testcaseType: isPublic ? "PUBLIC" : "HIDDEN",
        passed: !!result.passed,
        runtime: result.runtime,
        memory: result.memory,
        errorType: result.errorType,
        errorMessage: result.errorMessage,
        input: isPublic ? result.input : undefined,
        expectedOutput: isPublic ? result.expectedOutput : undefined,
        actualOutput: isPublic ? result.actualOutput : undefined
      }},
      { upsert: true }
    );
  });
  db.submission_jobs.updateOne({ _id: job._id }, { $unset: {
    testResults: "",
    publicTestCases: "",
    hiddenTestCases: ""
  }});
});
print("Legacy submission result migration completed; rerun safely if interrupted.");