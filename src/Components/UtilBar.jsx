import React from "react";
import { EditorContextAPI } from "./EditorContextAPI";
import { useContext } from "react";
function UtilBar() {
  let c=useContext(EditorContextAPI)
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
        <button className="bg-green-500 rounded-lg"> Import</button>
        <button className="bg-green-500 rounded-lg" onClick={()=>c.downloadCode(c.sourceCode,c.languageId)}>
          Download
        </button>

      </div>
    </div>
  );
}

export default UtilBar;
