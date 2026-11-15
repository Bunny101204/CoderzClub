import { apiBase } from "./institutionEnv.js";

export async function loginApi(request, username, password) {
  const response = await request.post(`${apiBase()}/api/login`, {
    data: { username, password },
  });
  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`login failed for ${username}: ${response.status()} ${body}`);
  }
  const data = await response.json();
  return data.token;
}

export async function apiJson(request, method, path, { token, orgId, data, expectedStatuses } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (orgId) headers["X-Organization-Id"] = orgId;
  const response = await request.fetch(`${apiBase()}${path}`, {
    method,
    headers,
    data,
  });
  const status = response.status();
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    json = text;
  }
  if (expectedStatuses && !expectedStatuses.includes(status)) {
    throw new Error(`${method} ${path} expected ${expectedStatuses.join("|")} got ${status}: ${text}`);
  }
  return { status, json, text };
}

export function toDatetimeLocal(date) {
  const pad = (n) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
