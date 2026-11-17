export async function apiJson(apiBase, method, path, { token, data, expectedStatuses = [200] } = {}) {
  const url = `${apiBase.replace(/\/$/, "")}${path}`;
  let response;
  try {
    response = await fetch(url, {
      method,
      headers: {
        Accept: "application/json",
        ...(data !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: data === undefined ? undefined : JSON.stringify(data),
    });
  } catch {
    throw new Error(`${method} ${path} failed before an HTTP response was received.`);
  }
  const text = await response.text();
  let body = text;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  if (!expectedStatuses.includes(response.status)) {
    throw new Error(`${method} ${path} returned HTTP ${response.status}.`);
  }
  return { status: response.status, body };
}

export async function loginAdmin(apiBase, { username, password }) {
  const login = await apiJson(apiBase, "POST", "/api/login", {
    data: { username, password },
    expectedStatuses: [200],
  });
  if (!login.body?.token) {
    throw new Error("Admin login did not return a token.");
  }
  return login.body.token;
}
