# DSA problem-bank seeder

Safe authenticated import for the curated curriculum in
`docs/problem-bank-curriculum.md`.

## Layout

- Public curriculum + this CLI live in the repository.
- Private oracles, hidden cases, manifests, and reports live in
  **gitignored** `.local/problem-bank/`.

## Commands

```powershell
# Guard unit tests
npm run test:problem-bank:guards

# Verify Phase 1 oracles against all generated cases (no API)
npm run problem-bank:verify

# Dry-run plan (default; no writes)
npm run problem-bank:seed

# Apply to isolated staging only
$env:PROBLEM_BANK_CREDENTIALS_FILE="C:\secure\staging-admin.json"
npm run problem-bank:seed -- --apply --api-base http://127.0.0.1:8080
```

Production hosts are refused unless `--allow-production` is also supplied after
backup and owner approval. Tokens and passwords are never printed.
