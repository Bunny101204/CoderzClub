# New Execution Model - Complete Implementation Guide

## Overview
Changing from **function-based execution** to **stdin/stdout based execution** (like HackerRank/Codeforces).

## Key Changes

### 1. **Standard Language Templates**
All problems now use the same template for each language, not problem-specific templates.

**Before (Function-based):**
```java
// Problem-specific template
public int twoSum(int[] nums, int target) {
    // User writes solution here
}
```

**After (Standard Template):**
```java
public class Main {
    public static void main(String[] args) {
        // User writes complete solution here
        // Reads from stdin, prints to stdout
    }
}
```

### 2. **Test Case Format Change**

**Before (Function Parameters):**
```json
{
  "input": [[2, 7, 11, 15], 9],  // Function parameters
  "output": 0                      // Return value
}
```

**After (Input/Output Strings):**
```json
{
  "input": "2 7 11 15\n9",        // stdin input
  "output": "0"                    // stdout output
}
```

## Implementation Steps

### Step 1: Update Language Templates ✅
Created `LanguageTemplates.js` with standard templates for all languages.

### Step 2: Update Problem Model

**Backend Changes Needed:**

#### Problem.java
```java
@Document(collection = "problems")
public class Problem {
    @Id
    private String id;
    private String title;
    private String description;
    private String difficulty; // EASY, MEDIUM, HARD
    private List<String> tags;
    
    // NEW: Remove function-based fields
    // private String functionName;
    // private List<Parameter> parameters;
    // private String returnType;
    
    // NEW: Add input/output based test cases
    private List<TestCase> publicTestCases;   // stdin/stdout format
    private List<TestCase> hiddenTestCases;   // stdin/stdout format
    
    // Optional: Add example input/output for display
    private String exampleInput;
    private String exampleOutput;
    private String exampleExplanation;
    
    // Optional: Constraints and hints
    private String constraints;
    private List<String> hints;
    
    // Rest of fields remain same...
}
```

#### TestCase Model
```java
public class TestCase {
    private String input;      // Raw stdin input
    private String output;     // Expected stdout output
    
    // Optional: For better display
    private String explanation; // Explain this test case
    
    // Getters and setters
}
```

### Step 3: Update ProblemPage Component

**Frontend Changes:**

```jsx
// ProblemPage.jsx
const ProblemPage = () => {
  const [problem, setProblem] = useState(null);
  const [languageId, setLanguageId] = useState(62); // Default Java
  const [editorCode, setEditorCode] = useState("");

  useEffect(() => {
    // When language changes, load appropriate template
    const template = getTemplate(languageId);
    setEditorCode(template);
  }, [languageId]);

  useEffect(() => {
    // Fetch problem data
    fetchProblem();
  }, [problemId]);

  return (
    <div>
      <ProblemDescription problem={problem} />
      <Judge0CodeEditor
        initialCode={editorCode}
        languageId={languageId}
        setLanguageId={setLanguageId}
        testCases={problem?.publicTestCases || []}
        hiddenTestCases={problem?.hiddenTestCases || []}
      />
    </div>
  );
};
```

### Step 4: Simplify Judge0CodeEditor

**Major Simplification:**
- Remove all function-based code generation
- Remove parameter parsing
- Direct stdin/stdout execution
- No code wrapping needed

```jsx
const Judge0CodeEditor = ({ initialCode, languageId, testCases, hiddenTestCases }) => {
  const [sourceCode, setSourceCode] = useState(initialCode);
  
  const handleRun = async () => {
    const results = [];
    
    for (const testCase of testCases) {
      const response = await postToJudge0({
        language_id: languageId,
        source_code: sourceCode,
        stdin: testCase.input,  // Direct input
      });
      
      const actual = response.data.stdout?.trim() || "";
      const expected = testCase.output.trim();
      const passed = actual === expected;
      
      results.push({ input: testCase.input, expected, actual, passed });
    }
    
    setResults(results);
  };

  const handleSubmit = async () => {
    // Test public cases
    for (const tc of publicTestCases) {
      const result = await runTestCase(tc);
      if (!result.passed) {
        return { status: "FAILED", failedCase: tc };
      }
    }
    
    // Test hidden cases
    for (const tc of hiddenTestCases) {
      const result = await runTestCase(tc);
      if (!result.passed) {
        return { status: "FAILED", message: "Failed on hidden test case" };
      }
    }
    
    return { status: "ACCEPTED" };
  };
};
```

### Step 5: Update AddProblem Component

**Problem Creation UI Changes:**

```jsx
const AddProblem = () => {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [difficulty, setDifficulty] = useState("EASY");
  const [publicTestCases, setPublicTestCases] = useState([
    { input: "", output: "", explanation: "" }
  ]);
  const [hiddenTestCases, setHiddenTestCases] = useState([
    { input: "", output: "" }
  ]);
  
  const addTestCase = (isPublic) => {
    if (isPublic) {
      setPublicTestCases([...publicTestCases, { input: "", output: "", explanation: "" }]);
    } else {
      setHiddenTestCases([...hiddenTestCases, { input: "", output: "" }]);
    }
  };
  
  const handleSubmit = async () => {
    const problemData = {
      title,
      description,
      difficulty,
      publicTestCases,
      hiddenTestCases,
      tags: tags.split(",").map(t => t.trim()),
    };
    
    await fetch("/api/problems", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(problemData)
    });
  };
  
  return (
    <form onSubmit={handleSubmit}>
      {/* Title, Description, Difficulty */}
      
      <div>
        <h3>Public Test Cases</h3>
        {publicTestCases.map((tc, idx) => (
          <div key={idx}>
            <textarea
              placeholder="Input (stdin)"
              value={tc.input}
              onChange={(e) => {
                const updated = [...publicTestCases];
                updated[idx].input = e.target.value;
                setPublicTestCases(updated);
              }}
            />
            <textarea
              placeholder="Expected Output (stdout)"
              value={tc.output}
              onChange={(e) => {
                const updated = [...publicTestCases];
                updated[idx].output = e.target.value;
                setPublicTestCases(updated);
              }}
            />
            <input
              placeholder="Explanation (optional)"
              value={tc.explanation}
              onChange={(e) => {
                const updated = [...publicTestCases];
                updated[idx].explanation = e.target.value;
                setPublicTestCases(updated);
              }}
            />
          </div>
        ))}
        <button type="button" onClick={() => addTestCase(true)}>
          Add Public Test Case
        </button>
      </div>
      
      <div>
        <h3>Hidden Test Cases</h3>
        {hiddenTestCases.map((tc, idx) => (
          <div key={idx}>
            <textarea
              placeholder="Input (stdin)"
              value={tc.input}
              onChange={(e) => {
                const updated = [...hiddenTestCases];
                updated[idx].input = e.target.value;
                setHiddenTestCases(updated);
              }}
            />
            <textarea
              placeholder="Expected Output (stdout)"
              value={tc.output}
              onChange={(e) => {
                const updated = [...hiddenTestCases];
                updated[idx].output = e.target.value;
                setHiddenTestCases(updated);
              }}
            />
          </div>
        ))}
        <button type="button" onClick={() => addTestCase(false)}>
          Add Hidden Test Case
        </button>
      </div>
      
      <button type="submit">Create Problem</button>
    </form>
  );
};
```

## Example Problem Structure

### Problem: Two Sum

**Old Format (Function-based):**
```json
{
  "title": "Two Sum",
  "description": "Find indices of two numbers that add up to target",
  "functionName": "twoSum",
  "parameters": [
    { "name": "nums", "type": "int[]" },
    { "name": "target", "type": "int" }
  ],
  "returnType": "int[]",
  "testCases": [
    {
      "input": [[2, 7, 11, 15], 9],
      "output": [0, 1]
    }
  ]
}
```

**New Format (Input/Output based):**
```json
{
  "title": "Two Sum",
  "description": "Given an array of integers nums and an integer target, return indices of the two numbers that add up to target.\n\nInput Format:\nFirst line: n (size of array)\nSecond line: n space-separated integers (array elements)\nThird line: target value\n\nOutput Format:\nTwo space-separated integers (indices of the two numbers)",
  "difficulty": "EASY",
  "tags": ["array", "hash-table"],
  "publicTestCases": [
    {
      "input": "4\n2 7 11 15\n9",
      "output": "0 1",
      "explanation": "nums[0] + nums[1] = 2 + 7 = 9"
    },
    {
      "input": "3\n3 2 4\n6",
      "output": "1 2",
      "explanation": "nums[1] + nums[2] = 2 + 4 = 6"
    }
  ],
  "hiddenTestCases": [
    {
      "input": "2\n3 3\n6",
      "output": "0 1"
    }
  ]
}
```

### Example Solutions

**Java:**
```java
import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        
        // Read input
        int n = sc.nextInt();
        int[] nums = new int[n];
        for (int i = 0; i < n; i++) {
            nums[i] = sc.nextInt();
        }
        int target = sc.nextInt();
        
        // Solve
        Map<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];
            if (map.containsKey(complement)) {
                // Print output
                System.out.println(map.get(complement) + " " + i);
                return;
            }
            map.put(nums[i], i);
        }
    }
}
```

**Python:**
```python
# Read input
n = int(input())
nums = list(map(int, input().split()))
target = int(input())

# Solve
seen = {}
for i, num in enumerate(nums):
    complement = target - num
    if complement in seen:
        # Print output
        print(seen[complement], i)
        break
    seen[num] = i
```

## Migration Strategy

### Phase 1: Update Backend (Do First)
1. Update Problem model
2. Update ProblemController
3. Update test case storage format
4. Keep old fields for backward compatibility initially

### Phase 2: Update Frontend
1. Add LanguageTemplates.js ✅
2. Update Judge0CodeEditor (remove function generation)
3. Update ProblemPage (use standard templates)
4. Update AddProblem (new test case format)

### Phase 3: Data Migration
1. Script to convert old problems to new format
2. Update existing problems in database
3. Remove deprecated fields

### Phase 4: Clean Up
1. Remove old function-based code
2. Remove parameter models
3. Update documentation

## Benefits of New Approach

### 1. **Simplicity**
- No complex code generation
- No parameter parsing
- Direct execution

### 2. **Flexibility**
- Users can write any code structure
- Support for multi-file programs
- Better for algorithm problems

### 3. **Industry Standard**
- Matches LeetCode playground, HackerRank, Codeforces
- Familiar to most programmers
- Better for competitive programming

### 4. **Easier Testing**
- Simple string comparison
- Clear input/output format
- Easier to debug

### 5. **More Control**
- Users control all I/O
- Can use any data structures
- Better for learning

## Disadvantages to Consider

### 1. **More Boilerplate**
- Users write more code (input parsing)
- Not as clean as function-based

### 2. **Input Parsing**
- Users must handle input parsing
- Can be error-prone for beginners

### 3. **Test Case Complexity**
- Need to format input as strings
- Output must be exact string match

## Hybrid Approach (Optional)

Keep both modes and let problem creators choose:

```java
public class Problem {
    private ExecutionMode mode; // FUNCTION or STDIN_STDOUT
    
    // For FUNCTION mode
    private String functionName;
    private List<Parameter> parameters;
    
    // For STDIN_STDOUT mode
    private List<TestCase> testCases;
}
```

This allows:
- Beginner problems: Function mode (cleaner)
- Advanced problems: Stdin/stdout mode (realistic)
- Algorithm competitions: Stdin/stdout mode

## Next Steps

1. ✅ Create LanguageTemplates.js
2. ⏳ Update Problem model in backend
3. ⏳ Create simplified Judge0CodeEditor
4. ⏳ Update AddProblem component
5. ⏳ Update ProblemPage component
6. ⏳ Test with sample problems
7. ⏳ Migrate existing problems
8. ⏳ Update documentation

## Conclusion

The new execution model is simpler, more flexible, and aligns with industry standards. It requires initial effort to migrate but provides long-term benefits in maintainability and user experience.



