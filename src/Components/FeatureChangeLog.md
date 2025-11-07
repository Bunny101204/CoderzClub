# Feature Change Log: Import Button & Context Improvements

This document details all code changes made to implement the Import button feature and improve context sharing in the CoderzClub project. Each change includes before/after code and an explanation.

---

## 1. Judge0CodeEditor.jsx: Add `setSourceCode` to Context Provider

**Why:**
To allow the UtilBar component to update the code editor content, we need to provide `setSourceCode` through the context.

**Before:**
```jsx
<EditorContextAPI.Provider
  value={{ setLanguageId, sourceCode, languageId, downloadCode }}
>
  <UtilBar />
</EditorContextAPI.Provider>
```

**After:**
```jsx
<EditorContextAPI.Provider
  value={{ setLanguageId, sourceCode, languageId, downloadCode, setSourceCode }}
>
  <UtilBar />
</EditorContextAPI.Provider>
```

**Explanation:**
By adding `setSourceCode` to the context, any child component (like UtilBar) can update the code in the editor, enabling features like file import.

---

## 2. UtilBar.jsx: Implement Import Button Functionality

**Why:**
To allow users to import code files, detect their language, and load them into the editor.

### a. Add a hidden file input and a ref
**Before:**
```jsx
function UtilBar() {
  let c=useContext(EditorContextAPI)
  return (
    ...
    <button className="bg-green-500 rounded-lg"> Import</button>
    ...
  );
}
```

**After:**
```jsx
import React, { useRef } from "react";
...
function UtilBar() {
  let c = useContext(EditorContextAPI);
  const fileInputRef = useRef();
  ...
  <button className="bg-green-500 rounded-lg" onClick={handleImportClick}> Import</button>
  <input
    type="file"
    ref={fileInputRef}
    style={{ display: "none" }}
    accept=".c,.cpp,.java,.py,.js,.ts,.go,.php,.rb"
    onChange={handleFileChange}
  />
  ...
}
```

**Explanation:**
- We use a hidden file input to let users pick files without showing the input directly.
- The `useRef` hook allows us to trigger the file input when the Import button is clicked.

### b. Supported extensions mapping and handlers
**Before:**
_No import logic existed._

**After:**
```jsx
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
```

**Explanation:**
- `extensionToLangId` maps file extensions to language IDs used by the editor.
- `handleImportClick` triggers the file picker.
- `handleFileChange` checks the file type, reads the file, and updates the editor and language if valid.

---

**Summary:**
These changes enable users to import code files into the editor, with automatic language detection and error handling for unsupported files. The context update allows UtilBar to control the editor content directly. 