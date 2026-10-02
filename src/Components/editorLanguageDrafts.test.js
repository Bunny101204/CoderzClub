import test from "node:test";
import assert from "node:assert/strict";
import { applyLanguageSwitch } from "./editorLanguageDrafts.js";

const templates = {
  62: "public class Main {\n    public static void main(String[] args) {\n        // Java\n    }\n}",
  71: "# Python template\n",
  54: "#include <iostream>\nint main() {\n    // C++\n    return 0;\n}",
};

const javaCode = "public class Main { /* student java */ }";
const pythonDraft = "print('saved python')";

test("Java to Python does not copy Java code", () => {
  const switched = applyLanguageSwitch({
    previousLanguageId: 62,
    nextLanguageId: 71,
    currentCode: javaCode,
    drafts: {},
    templates,
  });
  assert.equal(switched.drafts[62], javaCode);
  assert.equal(switched.code, templates[71]);
  assert.notEqual(switched.code, javaCode);
  assert.equal(Object.prototype.hasOwnProperty.call(switched.drafts, 71), false);
});

test("Java to C++ does not copy Java code", () => {
  const switched = applyLanguageSwitch({
    previousLanguageId: 62,
    nextLanguageId: 54,
    currentCode: javaCode,
    drafts: {},
    templates,
  });
  assert.equal(switched.drafts[62], javaCode);
  assert.equal(switched.code, templates[54]);
  assert.notEqual(switched.code, javaCode);
});

test("switching back to Java restores the Java draft", () => {
  const toPython = applyLanguageSwitch({
    previousLanguageId: 62,
    nextLanguageId: 71,
    currentCode: javaCode,
    drafts: {},
    templates,
  });
  const backToJava = applyLanguageSwitch({
    previousLanguageId: 71,
    nextLanguageId: 62,
    currentCode: toPython.code,
    drafts: toPython.drafts,
    templates,
  });
  assert.equal(backToJava.code, javaCode);
});

test("existing Python draft is restored when returning to Python", () => {
  const toPython = applyLanguageSwitch({
    previousLanguageId: 62,
    nextLanguageId: 71,
    currentCode: javaCode,
    drafts: { 71: pythonDraft },
    templates,
  });
  assert.equal(toPython.code, pythonDraft);
  const backToJava = applyLanguageSwitch({
    previousLanguageId: 71,
    nextLanguageId: 62,
    currentCode: pythonDraft,
    drafts: toPython.drafts,
    templates,
  });
  const backToPython = applyLanguageSwitch({
    previousLanguageId: 62,
    nextLanguageId: 71,
    currentCode: backToJava.code,
    drafts: backToJava.drafts,
    templates,
  });
  assert.equal(backToPython.code, pythonDraft);
});

test("missing language template yields an empty editor instead of Java", () => {
  const switched = applyLanguageSwitch({
    previousLanguageId: 62,
    nextLanguageId: 999,
    currentCode: javaCode,
    drafts: {},
    templates,
  });
  assert.equal(switched.code, "");
});
