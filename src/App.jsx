import { useState } from "react";
import reactLogo from "./assets/react.svg";
import viteLogo from "/vite.svg";
import "./App.css";
import Judge0CodeEditor from "./Components/Judge0CodeEditor";
function App() {
  const [count, setCount] = useState(0);

  return (
    <>
      <h1 className="font-bold text-5xl ml-185  ">CoderzClub</h1>
      <Judge0CodeEditor />
    </>
  );
}

export default App;
