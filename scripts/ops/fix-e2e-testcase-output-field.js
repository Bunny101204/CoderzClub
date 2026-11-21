// In-place repair for institution e2e problems: expectedOutput -> output.
// Does not drop databases or volumes.
const dbx = db.getSiblingDB("coderzclub_institution_e2e");
let fixed = 0;
dbx.problems.find({}).forEach((p) => {
  let changed = false;
  function fix(arr) {
    if (!Array.isArray(arr)) return arr;
    return arr.map((tc) => {
      if (tc && tc.expectedOutput != null && (tc.output == null || tc.output === "")) {
        changed = true;
        const n = Object.assign({}, tc);
        n.output = n.expectedOutput;
        delete n.expectedOutput;
        return n;
      }
      return tc;
    });
  }
  const pub = fix(p.publicTestCases);
  const hid = fix(p.hiddenTestCases);
  if (changed) {
    dbx.problems.updateOne({ _id: p._id }, { $set: { publicTestCases: pub, hiddenTestCases: hid } });
    fixed += 1;
    print("fixed " + p._id);
  }
});
print("fixedCount=" + fixed);
printjson(
  dbx.problems
    .find({}, { title: 1, publicTestCases: 1, hiddenTestCases: 1 })
    .toArray()
);
