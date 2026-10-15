import assert from "node:assert/strict";
import test from "node:test";
import { chooseStarterCode, getTemplate } from "./LanguageTemplates.js";

test("problem starter is used for its language", () => {
  assert.equal(chooseStarterCode(62, "public class Main {}", 62), "public class Main {}");
});

test("another language uses that language template", () => {
  assert.equal(chooseStarterCode(71, "public class Main {}", 62), getTemplate(71));
  assert.match(chooseStarterCode(71, "", 62), /Your code goes here/);
});

test("a blank problem starter falls back to the language template", () => {
  assert.equal(chooseStarterCode(54, "   ", 54), getTemplate(54));
});
