// Standard code templates for each programming language
// Users write their solution logic inside these templates

export const LANGUAGE_TEMPLATES = {
  // Java (language_id: 62)
  62: `public class Main {
    public static void main(String[] args) {
        // Your code goes here
        
    }
}`,

  // Python (language_id: 71)
  71: `# Your code goes here

`,

  // C (language_id: 50)
  50: `#include <stdio.h>
#include <stdlib.h>
#include <string.h>

int main() {
    // Your code goes here
    
    return 0;
}`,

  // C++ (language_id: 54)
  54: `#include <iostream>
#include <vector>
#include <string>
using namespace std;

int main() {
    // Your code goes here
    
    return 0;
}`,

  // JavaScript (Node.js) (language_id: 63)
  63: `// Your code goes here

`,

  // TypeScript (language_id: 74)
  74: `// Your code goes here

`,

  // Go (language_id: 60)
  60: `package main

import "fmt"

func main() {
    // Your code goes here
    
}`,

  // PHP (language_id: 68)
  68: `<?php
// Your code goes here

?>`,

  // Ruby (language_id: 72)
  72: `# Your code goes here

`,

  // C# (language_id: 51)
  51: `using System;

class Program {
    static void Main(string[] args) {
        // Your code goes here
        
    }
}`,

  // Rust (language_id: 73)
  73: `fn main() {
    // Your code goes here
    
}`,

  // Kotlin (language_id: 78)
  78: `fun main() {
    // Your code goes here
    
}`,

  // Swift (language_id: 83)
  83: `import Foundation

// Your code goes here

`,

  // Scala (language_id: 81)
  81: `object Main {
    def main(args: Array[String]): Unit = {
        // Your code goes here
        
    }
}`,
};

// Helper function to get template for a language
export const getTemplate = (languageId) => {
  return LANGUAGE_TEMPLATES[languageId] || LANGUAGE_TEMPLATES[62]; // Default to Java
};

// Language names mapping
export const LANGUAGE_NAMES = {
  50: "C",
  51: "C#",
  54: "C++",
  60: "Go",
  62: "Java",
  63: "JavaScript",
  68: "PHP",
  71: "Python",
  72: "Ruby",
  73: "Rust",
  74: "TypeScript",
  78: "Kotlin",
  81: "Scala",
  83: "Swift",
};

// File extensions mapping
export const FILE_EXTENSIONS = {
  50: "c",
  51: "cs",
  54: "cpp",
  60: "go",
  62: "java",
  63: "js",
  68: "php",
  71: "py",
  72: "rb",
  73: "rs",
  74: "ts",
  78: "kt",
  81: "scala",
  83: "swift",
};

// Supported languages for dropdown
export const SUPPORTED_LANGUAGES = [
  { id: 62, name: "Java" },
  { id: 71, name: "Python" },
  { id: 54, name: "C++" },
  { id: 50, name: "C" },
  { id: 51, name: "C#" },
  { id: 63, name: "JavaScript" },
  { id: 74, name: "TypeScript" },
  { id: 60, name: "Go" },
  { id: 68, name: "PHP" },
  { id: 72, name: "Ruby" },
  { id: 73, name: "Rust" },
  { id: 78, name: "Kotlin" },
  { id: 81, name: "Scala" },
  { id: 83, name: "Swift" },
];



