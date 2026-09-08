
 import React from 'react'

export function downloadCode(sourceCode,languageId){
  const code = sourceCode;
    console.log(languageId);
  // Optional: Let file type depend on selected language
  
  const languages={
        50:"C",
        54:"C++",
        62:"Java",
        71:"Python",
        63:"JavaScript",
        74:"TypeScript",
        60:"Go",
        68:"PHP",
        72:"Ruby"
  }
  const language = languages[languageId];
  
  const extension = {
    "Java": "java",
    "C": "c",
    "C++": "cpp",
    "Python": "py",
    "JavaScript": "js",
    "txt": "txt"
  }[language] || "txt";

  const blob = new Blob([code], { type: "text/plain" });
  const link = document.createElement("a");
  link.download = `code.${extension}`;
  link.href = URL.createObjectURL(blob);
  link.click();
}


 
 