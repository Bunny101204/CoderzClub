# Import Button Feature Documentation

This document explains how the **Import** button was implemented in the UtilBar component of the CoderzClub project. The Import button allows users to load code files into the editor, automatically setting the language based on the file extension.

---

## 1. Requirements
- User clicks Import → file picker opens.
- Only supported code files can be selected (`.c`, `.cpp`, `.java`, `.py`, `.js`, `.ts`, `.go`, `.php`, `.rb`).
- The editor language changes based on the file extension.
- The file content is loaded into the code editor.
- If the file type is not supported, an error is shown.

---

## 2. Update Context Provider
To allow UtilBar to update the code editor, add `setSourceCode` to the context provider in `Judge0CodeEditor.jsx`:

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

---

## 3. Implement Import Logic in UtilBar.jsx

### a. Add a hidden file input and a ref
```jsx
const fileInputRef = useRef();
```

### b. Supported extensions mapping
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
```

### c. Handle Import button click
```jsx
const handleImportClick = () => {
  fileInputRef.current.click();
};
```

### d. Handle file selection and loading
```jsx
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

### e. Add the hidden file input and connect everything
```jsx
<button className="bg-green-500 rounded-lg" onClick={handleImportClick}> Import</button>
<input
  type="file"
  ref={fileInputRef}
  style={{ display: "none" }}
  accept=".c,.cpp,.java,.py,.js,.ts,.go,.php,.rb"
  onChange={handleFileChange}
/>
```

---

## 4. Preview of the Final UtilBar Component
```jsx
function UtilBar() {
  let c = useContext(EditorContextAPI);
  const fileInputRef = useRef();
  const extensionToLangId = { /* ... as above ... */ };
  // ...handlers as above...
  return (
    <div className="mb-3">
      {/* ... */}
      <button onClick={handleImportClick}>Import</button>
      <input type="file" ref={fileInputRef} style={{ display: "none" }} accept=".c,.cpp,.java,.py,.js,.ts,.go,.php,.rb" onChange={handleFileChange} />
      {/* ... */}
    </div>
  );
}
```

---

## 5. Usage
- Click **Import** and select a supported code file.
- The code and language will update in the editor.
- If you select an unsupported file, an alert will appear.

---

**You can extend this feature further by adding UI feedback, error messages, or supporting more languages as needed!** 