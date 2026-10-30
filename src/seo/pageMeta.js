export const CANONICAL_ORIGIN = "https://coderzclub.site";

const PUBLIC_PAGES = {
  "/": {
    title: "CoderzClub — Practice Coding",
    description: "Practice coding problems, enter contests, and track your progress on CoderzClub.",
    index: true,
  },
  "/about": {
    title: "About — CoderzClub",
    description: "CoderzClub is a practice site for coding problems, contests, and classroom batches.",
    index: true,
  },
  "/landing": {
    title: "About — CoderzClub",
    description: "CoderzClub is a practice site for coding problems, contests, and classroom batches.",
    index: true,
  },
  "/privacy": {
    title: "Privacy — CoderzClub",
    description: "How the CoderzClub application stores and uses account data.",
    index: true,
  },
  "/terms": {
    title: "Terms — CoderzClub",
    description: "Notes describing how the CoderzClub service currently works.",
    index: true,
  },
  "/auth": {
    title: "Sign in — CoderzClub",
    description: "Sign in or create a CoderzClub account.",
    index: true,
  },
};

export function metaForPath(pathname) {
  const path = pathname || "/";
  if (PUBLIC_PAGES[path]) {
    return { path, ...PUBLIC_PAGES[path] };
  }
  if (path.startsWith("/admin")) {
    return {
      path,
      title: "Admin — CoderzClub",
      description: "CoderzClub administration.",
      index: false,
    };
  }
  if (path.startsWith("/problem/")) {
    return {
      path,
      title: "Problem — CoderzClub",
      description: "A coding problem on CoderzClub.",
      index: false,
    };
  }
  if (path.startsWith("/contests")) {
    return {
      path,
      title: "Contests — CoderzClub",
      description: "Scheduled coding contests on CoderzClub.",
      index: false,
    };
  }
  if (path === "/home" || path === "/bundles" || path === "/leaderboard" || path === "/editor") {
    const name = path.slice(1, 2).toUpperCase() + path.slice(2);
    return {
      path,
      title: `${name} — CoderzClub`,
      description: "CoderzClub practice tools.",
      index: false,
    };
  }
  return {
    path,
    title: "Not found — CoderzClub",
    description: "This CoderzClub page does not exist.",
    index: false,
  };
}

export function canonicalUrl(pathname) {
  const meta = metaForPath(pathname);
  if (!meta.index) {
    return null;
  }
  return `${CANONICAL_ORIGIN}${meta.path === "/" ? "/" : meta.path}`;
}
