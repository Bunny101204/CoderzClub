import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  DESKTOP_NAV_CLASS,
  MOBILE_MENU_BUTTON_CLASS,
  MOBILE_NAV_CLASS,
  navigationItems,
  reduceMobileMenu,
} from "./mainNav.js";

const header = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), "../Components/Header.jsx"),
  "utf8"
);

test("signed-in users get the main routes and admins also get Admin", () => {
  const user = navigationItems({ isAuthenticated: true, role: "USER" }).map((item) => item.label);
  assert.deepEqual(user, ["Problems", "Contests", "Bundles", "Editor", "Leaderboard", "My batches"]);
  const admin = navigationItems({ isAuthenticated: true, role: "ADMIN" }).map((item) => item.to);
  assert.ok(admin.includes("/admin"));
  assert.deepEqual(navigationItems({ isAuthenticated: false, role: "ADMIN" }), []);
  assert.equal(navigationItems({ isAuthenticated: true, role: "user" }).some((item) => item.to === "/admin"), false);
});

test("mobile menu closes on navigation, Escape, and route changes, and toggles open", () => {
  assert.equal(reduceMobileMenu(false, { type: "toggle" }), true);
  assert.equal(reduceMobileMenu(true, { type: "navigate" }), false);
  assert.equal(reduceMobileMenu(true, { type: "escape" }), false);
  assert.equal(reduceMobileMenu(true, { type: "route" }), false);
});

test("header exposes an accessible mobile menu without removing theme or profile controls", () => {
  assert.match(DESKTOP_NAV_CLASS, /hidden md:flex/);
  assert.match(MOBILE_MENU_BUTTON_CLASS, /md:hidden/);
  assert.match(MOBILE_NAV_CLASS, /md:hidden/);
  assert.match(header, /aria-expanded=\{menuOpen\}/);
  assert.match(header, /aria-controls="mobile-nav"/);
  assert.match(header, /aria-label=\{menuOpen \? "Close menu" : "Open menu"\}/);
  assert.match(header, /event\.key === "Escape"/);
  assert.match(header, /type: "navigate"/);
  assert.match(header, /ThemeToggleIcon/);
  assert.match(header, /navigate\('\/profile'\)/);
  assert.match(header, /overflow-x|min-w-0|truncate/);
  assert.doesNotMatch(header, /hidden md:flex items-center space-x-2[\s\S]*Admin[\s\S]*hidden md:flex/);
});
