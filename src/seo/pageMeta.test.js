import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { canonicalUrl, metaForPath } from "./pageMeta.js";

test("public pages have distinct titles and canonical urls", () => {
  assert.equal(metaForPath("/").title, "CoderzClub — Practice Coding");
  assert.equal(canonicalUrl("/"), "https://coderzclub.site/");
  assert.equal(canonicalUrl("/privacy"), "https://coderzclub.site/privacy");
  assert.equal(metaForPath("/privacy").index, true);
});

test("private and unknown pages are not canonicalized to the homepage", () => {
  assert.equal(canonicalUrl("/admin"), null);
  assert.equal(canonicalUrl("/profile"), null);
  assert.equal(canonicalUrl("/missing-page"), null);
  assert.equal(metaForPath("/admin").index, false);
  assert.match(metaForPath("/missing-page").title, /Not found/);
});

test("the initial html exposes homepage metadata and a favicon", () => {
  const html = readFileSync(new URL("../../index.html", import.meta.url), "utf8");
  assert.match(html, /rel="canonical" href="https:\/\/coderzclub.site\/"/);
  assert.match(html, /property="og:title"/);
  assert.match(html, /name="description"/);
  assert.match(html, /href="\/favicon.svg"/);
  const robots = readFileSync(new URL("../../public/robots.txt", import.meta.url), "utf8");
  assert.match(robots, /Allow: \//);
  assert.match(robots, /Disallow: \/admin/);
  const sitemap = readFileSync(new URL("../../public/sitemap.xml", import.meta.url), "utf8");
  assert.match(sitemap, /https:\/\/coderzclub.site\/privacy/);
  assert.doesNotMatch(sitemap, /\/admin/);
});
