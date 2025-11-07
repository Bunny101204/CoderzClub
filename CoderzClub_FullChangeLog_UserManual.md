# CoderzClub Full Change Log & User Manual

This document details all changes made to the CoderzClub project (frontend and backend), with explanations for why and how each change was made. It is designed as a user manual for building, running, and understanding the app.

---

## 1. Frontend (React)

### 1.1 Import Button Feature
- **What:** Added an Import button to UtilBar for loading code files into the editor.
- **Why:** To allow users to import code files, auto-detect language, and load code into the editor.
- **How:**
  - Added a hidden file input and a React ref.
  - On Import click, triggers the file input.
  - On file selection, checks extension, sets language, and loads code.
  - Only allows supported file types.
- **Usage:** Click Import, select a supported file, and the code/language will update in the editor.

### 1.2 Login/Registration Flow
- **What:** Added a login page with hardcoded admin credentials, and registration via backend.
- **Why:** To secure admin features and allow user registration.
- **How:**
  - Created `LoginPage.jsx` with a form for username/password.
  - On successful login, sets admin state and redirects to admin dashboard.
  - Registration is handled via backend API.
- **Usage:** On app load, users see the login page unless already logged in.

### 1.3 Admin Dashboard Setup
- **What:** Added an admin dashboard for problem management (add/edit problems, test cases, templates).
- **Why:** To allow admins to manage coding problems and test cases.
- **How:**
  - Created `AdminPage` component.
  - Added forms for problem creation and listing.
  - Problems are stored in app state (or backend, if connected).

### 1.4 State Management for Problems
- **What:** Moved problems array to App state and passed as props.
- **Why:** To allow dynamic problem management and sharing between components.
- **How:**
  - Used `useState` in `App.jsx` for problems.
  - Passed `problems` and `setProblems` to HomePage, ProblemPage, AdminPage.

### 1.5 Routing and Authentication Persistence
- **What:** Added routing for login, admin, and problem pages. Persisted login state in localStorage.
- **Why:** To protect admin routes and remember login across reloads.
- **How:**
  - Used React Router for navigation.
  - Used localStorage to persist `isAdmin` state.
  - Redirects to login if not authenticated.
- **Usage:** On reload, users stay logged in/out as before.

---

## 2. Backend (Spring Boot)

### 2.1 Project Structure and Dependencies
- **What:** Created a `backend/` folder with a Maven Spring Boot project.
- **Why:** To provide a robust, scalable backend for user, problem, and submission management.
- **How:**
  - Added `pom.xml` with dependencies for web, MongoDB, security, JWT, Lombok, validation.
  - Created standard Spring Boot folder structure.

### 2.2 MongoDB Atlas Connection
- **What:** Configured MongoDB Atlas as the database.
- **Why:** To store users, problems, and submissions in the cloud.
- **How:**
  - Set `spring.data.mongodb.uri` in `application.properties`.
  - Used MongoDB Atlas connection string.

### 2.3 Models and Repositories
- **What:** Added models for User, Problem, Submission, and their repositories.
- **Why:** To define and access data in MongoDB.
- **How:**
  - Used Lombok for less boilerplate.
  - Created repository interfaces extending `MongoRepository`.

### 2.4 Security (JWT, Password Hashing)
- **What:** Implemented JWT-based authentication and password hashing.
- **Why:** To secure endpoints and store passwords safely.
- **How:**
  - Added `SecurityConfig.java` for Spring Security setup.
  - Used BCrypt for password hashing.
  - Created `JwtUtil.java` for JWT creation/validation.
  - Added `JwtFilter.java` to validate JWT on requests.

### 2.5 Auth Endpoints (Register/Login)
- **What:** Added `/api/register` and `/api/login` endpoints.
- **Why:** To allow user registration and login with JWT.
- **How:**
  - Created `AuthController.java` for REST endpoints.
  - Used `UserService.java` for business logic.
  - On login, returns a JWT token for use in future requests.
- **Usage:**
  - Register: POST `/api/register` with `{ username, email, password }`
  - Login: POST `/api/login` with `{ username, password }` (returns `{ token }`)

### 2.6 Troubleshooting Tips
- **If you get ECONNREFUSED:** Make sure the backend is running (`mvn spring-boot:run`) and listening on the correct port.
- **If files are empty:** Replace them with the full code provided in this manual.
- **Testing:** Use Postman or curl to test registration and login endpoints.

---

## 3. General Usage & Next Steps
- **Frontend:** Start with `npm run dev` in the CoderzClub folder.
- **Backend:** Start with `mvn spring-boot:run` in the backend folder.
- **Connect frontend to backend:** Update API calls in React to use the backend endpoints for registration, login, and problem management.
- **Extend:** Add more endpoints for problem CRUD, submissions, and admin features as needed.

---

**This manual is designed to help you (or any developer) understand, run, and extend the CoderzClub app. For any missing code, refer to the code blocks in this document or ask for specific file contents!** 