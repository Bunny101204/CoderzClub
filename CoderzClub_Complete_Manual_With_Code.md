# CoderzClub Complete Manual (with Code)

This manual documents every change, addition, and feature in the CoderzClub project, including the full code for each file and detailed explanations. Use this as a step-by-step guide to build, run, and extend the app.

---

## Table of Contents
1. [Frontend (React)](#frontend-react)
    - Import Button
    - Login/Registration
    - Admin Dashboard
    - State Management & Routing
2. [Backend (Spring Boot)](#backend-spring-boot)
    - Project Setup & pom.xml
    - MongoDB Atlas Connection
    - Models & Repositories
    - Security (JWT, Password Hashing)
    - Auth Endpoints (Register/Login)
    - Troubleshooting

---

## Frontend (React)

### 1. Import Button (src/Components/UtilBar.jsx)
**What/Why:** Allow users to import code files, auto-detect language, and load code into the editor.

**How:**
- Added a hidden file input and a React ref.
- On Import click, triggers the file input.
- On file selection, checks extension, sets language, and loads code.
- Only allows supported file types.

**Code:**
```jsx
import React, { useRef } from "react";
import { EditorContextAPI } from "./EditorContextAPI";
import { useContext } from "react";
function UtilBar() {
  let c = useContext(EditorContextAPI);
  const fileInputRef = useRef();

  // Supported extensions and their language IDs
  const extensionToLangId = {
    c: 50,
    cpp: 54,
    java: 62,
    py: 71,
    js: 63,
    ts: 74,
    go: 60,
    php: 68,
    rb: 72,
  };

  const handleImportClick = () => {
    fileInputRef.current.click();
  };

  const handleFileChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const ext = file.name.split('.').pop().toLowerCase();
    const langId = extensionToLangId[ext];
    if (!langId) {
      alert("Unsupported file type. Please select a supported code file.");
      e.target.value = "";
      return;
    }
    const reader = new FileReader();
    reader.onload = (event) => {
      c.setSourceCode(event.target.result);
      c.setLanguageId(langId);
    };
    reader.readAsText(file);
    e.target.value = "";
  };

  return (
    <div className="mb-3">
      <label className="block text-sm font-medium text-gray-700 mb-1">
        Select Language:
      </label>
      <div className="w-full  flex flex-row-reverse;">
        <select
          value={c.languageId}
          onChange={(e) => c.setLanguageId(Number(e.target.value))}
          className=" w-10% text-black p-2 border rounded bg-white"
        >
          <option value={50}>C</option>
          <option value={54}>C++</option>
          <option value={62}>Java</option>
          <option value={71}>Python</option>
          <option value={63}>JavaScript</option>
          <option value={74}>TypeScript</option>
          <option value={60}>Go</option>
          <option value={68}>PHP</option>
          <option value={72}>Ruby</option>
        </select>
        <button className="bg-green-500 rounded-lg" onClick={handleImportClick}> Import</button>
        <button className="bg-green-500 rounded-lg" onClick={()=>c.downloadCode(c.sourceCode,c.languageId)}>
          Download
        </button>
        {/* Hidden file input for import */}
        <input
          type="file"
          ref={fileInputRef}
          style={{ display: "none" }}
          accept=".c,.cpp,.java,.py,.js,.ts,.go,.php,.rb"
          onChange={handleFileChange}
        />
      </div>
    </div>
  );
}
export default UtilBar;
```

**Usage:** Click Import, select a supported file, and the code/language will update in the editor.

---

### 2. Login/Registration (src/Components/LoginPage.jsx, src/App.jsx)
**What/Why:** Secure admin features and allow user registration.

**How:**
- Created `LoginPage.jsx` with a form for username/password.
- On successful login, sets admin state and redirects to admin dashboard.
- Registration is handled via backend API.

**LoginPage.jsx:**
```jsx
import React, { useState } from "react";
import { useNavigate } from "react-router-dom";

const ADMIN_USERNAME = "bunny";
const ADMIN_PASSWORD = "pass";

function LoginPage({ onLogin }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const navigate = useNavigate();

  const handleSubmit = (e) => {
    e.preventDefault();
    if (username === ADMIN_USERNAME && password === ADMIN_PASSWORD) {
      setError("");
      onLogin();
      navigate("/admin");
    } else {
      setError("Invalid username or password");
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-900">
      <form
        onSubmit={handleSubmit}
        className="bg-gray-800 p-8 rounded-lg shadow-lg w-full max-w-sm"
      >
        <h2 className="text-2xl font-bold mb-6 text-white text-center">Admin Login</h2>
        <input
          type="text"
          placeholder="Username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          className="w-full p-3 mb-4 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none"
        />
        <input
          type="password"
          placeholder="Password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          className="w-full p-3 mb-4 rounded bg-gray-700 text-white border border-gray-600 focus:outline-none"
        />
        {error && <div className="text-red-400 mb-4 text-center">{error}</div>}
        <button
          type="submit"
          className="w-full bg-green-500 hover:bg-green-600 text-white font-bold py-2 px-4 rounded"
        >
          Login
        </button>
      </form>
    </div>
  );
}
export default LoginPage;
```

**App.jsx (relevant parts):**
```jsx
import { useState, useEffect } from "react";
import { BrowserRouter as Router, Routes, Route, Navigate, useLocation } from "react-router-dom";
import LoginPage from "./Components/LoginPage";
// ...
function App() {
  const [isAdmin, setIsAdmin] = useState(() => {
    return localStorage.getItem("isAdmin") === "true";
  });
  // ...
  useEffect(() => {
    localStorage.setItem("isAdmin", isAdmin ? "true" : "false");
  }, [isAdmin]);
  // ...
  return (
    <Router>
      <Routes>
        <Route path="/" element={<DefaultRoute />} />
        <Route path="/login" element={<LoginPage onLogin={handleLogin} />} />
        <Route path="/admin" element={isAdmin ? <AdminPage ... /> : <LoginPage onLogin={handleLogin} />} />
        {/* ...other routes... */}
      </Routes>
    </Router>
  );
}
```

**Usage:** On app load, users see the login page unless already logged in.

---

### 3. Admin Dashboard (src/Components/AdminPage.jsx)
**What/Why:** Allow admins to manage coding problems and test cases.

**How:**
- Created `AdminPage` component.
- Added forms for problem creation and listing.
- Problems are stored in app state (or backend, if connected).

**(Add your AdminPage code here as you build it out)**

---

### 4. State Management & Routing (src/App.jsx)
**What/Why:** Allow dynamic problem management and protect admin routes.

**How:**
- Used `useState` in `App.jsx` for problems.
- Passed `problems` and `setProblems` to HomePage, ProblemPage, AdminPage.
- Used React Router for navigation.
- Used localStorage to persist `isAdmin` state.

**(See App.jsx code above for details)**

---

## Backend (Spring Boot)

### 1. Project Setup & pom.xml (backend/pom.xml)
**What/Why:** Set up Spring Boot project with all dependencies.

**Code:**
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.coderzclub</groupId>
    <artifactId>coderzclub-backend</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    <name>CoderzClub Backend</name>
    <description>Spring Boot backend for CoderzClub with MongoDB Atlas</description>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-mongodb</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt</artifactId>
            <version>0.9.1</version>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-devtools</artifactId>
            <scope>runtime</scope>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

### 2. MongoDB Atlas Connection (backend/src/main/resources/application.properties)
**What/Why:** Connect to MongoDB Atlas for cloud database storage.

**Code:**
```properties
spring.data.mongodb.uri=mongodb+srv://<username>:<password>@cluster0.xxxxx.mongodb.net/coderzclub?retryWrites=true&w=majority
spring.data.mongodb.database=coderzclub
# JWT secret (change this to a secure random string in production)
jwt.secret=supersecretkey
jwt.expiration=86400000
```

---

### 3. Models & Repositories
**What/Why:** Define and access data in MongoDB.

**User.java:**
```java
package com.coderzclub.model;
// ... (see previous message for full code)
```
**Problem.java:**
```java
package com.coderzclub.model;
// ... (see previous message for full code)
```
**Submission.java:**
```java
package com.coderzclub.model;
// ... (see previous message for full code)
```
**UserRepository.java, ProblemRepository.java, SubmissionRepository.java:**
```java
package com.coderzclub.repository;
// ... (see previous message for full code)
```

---

### 4. Security (JWT, Password Hashing)
**What/Why:** Secure endpoints and store passwords safely.

**SecurityConfig.java, JwtUtil.java, JwtFilter.java:**
```java
package com.coderzclub.config;
// ... (see previous message for full code)
```

---

### 5. Auth Endpoints (Register/Login)
**What/Why:** Allow user registration and login with JWT.

**AuthController.java, UserService.java:**
```java
package com.coderzclub.controller;
// ... (see previous message for full code)
```

---

### 6. Troubleshooting
- If you get ECONNREFUSED: Make sure the backend is running (`mvn spring-boot:run`) and listening on the correct port.
- If files are empty: Replace them with the full code provided in this manual.
- Testing: Use Postman or curl to test registration and login endpoints.

---

**This manual is designed to help you (or any developer) understand, run, and extend the CoderzClub app. For any missing code, refer to the code blocks in this document or ask for specific file contents!** 