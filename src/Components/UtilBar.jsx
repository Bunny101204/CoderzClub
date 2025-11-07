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
      // console.log(event)
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
