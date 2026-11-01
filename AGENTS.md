# CoderzClub project rules

- Work autonomously: investigate, implement, test, debug, and retry reasonable local alternatives. Resolve routine questions using repository evidence and tests; ask only when genuinely blocked or outside these boundaries.
- Keep changes minimal, production-quality, and consistent with the architecture. Run relevant tests and distinguish verified results from assumptions.
- Local Docker Desktop, isolated test databases, Redis, RabbitMQ, development tools, and builds are permitted. Never use production MongoDB Atlas for development or integration tests.
- Never modify live servers, production data, Cloudflare deployments, or GCP infrastructure without explicit authorization.
- Never commit, push, create pull requests, or deploy unless the current task explicitly authorizes it. Do not add agent co-author attribution to commits.
- Stay on the existing `main` branch unless explicitly instructed otherwise.
- Preserve the Git stash, unrelated working-tree changes and untracked files, user credentials, and historical backups. Do not use destructive Git commands or discard unrelated work.
- Avoid unnecessary explanations, repeated approval requests, and stopping after recoverable local errors. Give concise progress updates during long tasks and one comprehensive completion report.
