# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Babel](https://babeljs.io/) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh

## Expanding the ESLint configuration

If you are developing a production application, we recommend using TypeScript with type-aware lint rules enabled. Check out the [TS template](https://github.com/vitejs/vite/tree/main/packages/create-vite/template-react-ts) for information on how to integrate TypeScript and [`typescript-eslint`](https://typescript-eslint.io) in your project.

## Backend Email Delivery

The backend uses Spring Boot Mail for real email delivery. To enable it, set SMTP values in environment variables or `backend/src/main/resources/application.properties`:

- `SPRING_MAIL_HOST`
- `SPRING_MAIL_PORT`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `APP_EMAIL_FROM` (optional)
- `APP_BACKEND_URL` (for verification/reset links)

Example for Gmail SMTP:

```bash
export SPRING_MAIL_HOST=smtp.gmail.com
export SPRING_MAIL_PORT=587
export SPRING_MAIL_USERNAME=your.email@gmail.com
export SPRING_MAIL_PASSWORD=your-app-password
export APP_EMAIL_FROM=your.email@gmail.com
export APP_BACKEND_URL=http://localhost:8080
```

## Judge0 Execution Key

The Judge0 integration requires a private API key for backend execution. Do not commit this key to source control.

Set it through environment variables or a local `.env` file in the repo root:

- `JUDGE0_API_KEY`

Example:

```bash
export JUDGE0_API_KEY=your-judge0-api-key
```

Since `.env` is already ignored by git, you can also place it in the repository root:

```env
JUDGE0_API_KEY=your-judge0-api-key
```

Do not expose this key in frontend/Vite environment variables such as `VITE_JUDGE0_API_KEY`.

If you need a fallback property, the backend also supports `judge0.api.key` in `backend/src/main/resources/application.properties`.

## Local Redis / submission limits
The backend uses Redis for submission rate limiting. If you do not have Redis running locally, enable fail-open mode:

```bash
export SUBMISSION_LIMIT_REDIS_FAIL_OPEN=true
```

This lets submissions continue on local development without Redis, while still using Redis when available.
