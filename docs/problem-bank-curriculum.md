# CoderzClub curated DSA problem bank — implementation specification

**Status:** Curriculum proposal; NOT imported. This is a source-of-truth checklist for Cursor/Codex to implement and independently verify before any backend write.

## Objective

Populate the CoderzClub practice catalog with 187 original, clearly specified stdin/stdout problems across 17 topic bundles, including EASY (64), MEDIUM (100) and HARD (23). Do not misrepresent draft content as reviewed or published.

## Current backend contracts (verified in repository)

- Problem creation: authenticated `POST /api/problems`; platform administrator can create platform-scoped PUBLIC practice problems; server allocates `numericId` when omitted and **ignores client-provided document `id`**. The response returns the stored database ID.
- Problem editing: authenticated `PATCH /api/problems/{id}/content`; protected admin view: `GET /api/problems/{id}/admin`.
- Bundle creation: ADMIN-only `POST /api/bundles`; bundle listing for admin: `GET /api/bundles/admin/all`; details: `GET /api/bundles/{id}/problems`.
- Problem model supports `title`, `statement`, `difficulty` (EASY/MEDIUM/HARD), `category`, `tags`, `inputFormat`, `outputFormat`, `constraints`, `hints`, `exampleInput`, `exampleOutput`, `exampleExplanation`, `publicTestCases`, `hiddenTestCases`, `testcaseVersion`, `executionMode`, `points`, `estimatedTime`, `availability`.
- Use `STANDARD_PER_CASE` execution mode, **not** `BATCH_STDIN_PROGRAM` (explicitly unsupported by current compatibility service).
- Bundle model supports ordered `problemIds`, `name`, `description`, `difficulty`, `category`, `tags`, `visibility`, `isActive`, `isPremium`, `totalPoints`, `estimatedTotalTime`. IDs must be canonical server-returned Mongo IDs, not assumed numeric aliases.
- Current configured default testcase limits: 500 cases per problem; 1 MB maximum input and output per testcase, but actual judge cost/timeout/limits must also be respected.

## Content authoring and judging contract

1. Write **original** descriptions, examples, explanations, constraints, I/O formats, and hints; do not copy proprietary problem statements or premium test data.
2. All problems must have deterministic, exact stdout expectations (including ordering, indices, whitespace, empty-result sentinel, overflow behavior, indexing convention, graph input order, tie breaking, tree/list encodings). If a common formulation permits multiple valid answers, specify a single canonical answer or add a custom checker and tests before publication. Example: Two Sum chooses lexicographically smallest zero-based pair, or constrain to exactly one pair.
3. Each EASY problem has at least 2 public examples and 6 hidden tests; MEDIUM at least 2 public and 12 hidden; HARD at least 3 public and 20 hidden, within resource limits. Test variety matters more than sheer count. Scale large stress cases only when judge time/memory can accommodate accepted reference solutions.
4. All valid inputs—including smallest allowed sizes, empty collections if explicitly permitted, equal values, negative values, repetitions, overflow-prone totals, sorted/reverse-sorted patterns, disconnected graphs, multiple components and maximal sizes when relevant—must be handled correctly. **Do not suppress valid boundary conditions merely to make a problem EASY**.
5. Use legitimate educational variants where wanted: `Move Zeroes` vs `Move Zeroes 2`, `Rotate Array` vs `Rotate Array 2`, `Two Sum` vs `Two Sum 2`, `Remove Duplicates from Sorted Array` vs `... 2`. EASY versions use genuinely narrower documented constraints or simpler operations; advanced versions introduce distinct algorithmic or space/time requirements. Suffix alone does not justify increasing difficulty.
6. Keep statements consistent with grader: e.g. if `n=0` is allowed, give a valid zero-element stdin shape; for graph traversal define node order and tie-breaking; for linked lists/trees encode data as a deterministic stdin format (no function-harness assumptions).
7. Build an **independent reference implementation/oracle** for every problem. Every case must be verified by the oracle and checked by a second method where feasible; compare Python/Java/C++ accepted implementations on a representative subset of cases. Include negative candidate solutions/mutations to demonstrate hidden cases distinguish common wrong answers.
8. Keep hidden cases, oracle source code, and any secret RNG seed in a **private** controlled location: `Bunny101204/CoderzClub` is a PUBLIC GitHub repository. Do not commit hidden cases, paid answer banks, private sample solutions, or credentials to this repository. Visible descriptions and curated topic list can remain public.
9. Use an explicit, dry-run-first, authenticated API import tool (Python or Node CLI). No automatic startup seeding. Require `--apply` for writes, and allow an explicit staging URL; decline production URLs unless an additional explicit flag is set and backup verified. No direct MongoDB editing by default.
10. Make import idempotent and resumable: distinguish catalog-managed problems from existing user/organization problems, never overwrite unrelated same-title problems, track returned server IDs in a local/private manifest, reconcile before writing, and map bundle memberships using canonical IDs. When reconciling changes, use protected admin endpoints and avoid leaking hidden tests to logs/reports.
11. Create/update bundles only after constituent problems pass quality gates. All bundle totals and positions must match stored objects; ensure expected public visibility without leaking organizational content.
12. Verification before writing: validate every example, input parser, expected output, testcase count and input/output sizes, unique title in scope, category/tag consistency, difficulty assignment, and accepted reference solutions. After staging import, verify pagination/search and bundle membership through backend APIs and confirm hidden tests are absent from student/public responses.
13. Deploy stages: generate full content offline; import batches of 10–20 to staging; confirm judge submissions and API responses; review difficulty in student pilots; only then promote approved content to production with a backup and explicit authorization. Keep a machine-readable report of succeeded/skipped/failed content; never claim unfinished items were published.

## Difficulty guidance

- EASY: primarily linear scan/simple maps or basic simulation under constrained inputs; correct for *all* documented cases.
- MEDIUM: multi-step reasoning; standard two-pointers, sliding window, binary search on answer, graph traversal, DP, or careful data structure use.
- HARD: advanced optimization or multi-algorithm reasoning under larger constraints; justify difficulty from algorithmic complexity rather than arbitrary surprise cases.

## Bundle recommendations

Create one ordered free/public bundle per category listed below, with beginner-to-advanced progression and user-facing descriptions. Consider later separate `EASY Starter`, `MEDIUM Interview` and `HARD Challenge` collections after backend bundle semantics and dashboard filters are verified. Set `isPremium=false` and `price=0` until the business model is proven. Do not quietly publish unvalidated paid bundles.

## Curated curriculum

### 1. Programming Foundations (10 problems)
Category: `ALGORITHMS`

1. Sum of Two Integers — **EASY**
2. Even or Odd — **EASY**
3. Maximum of Three Numbers — **EASY**
4. Factorial of N — **EASY**
5. Count Digits — **EASY**
6. Reverse Digits — **EASY**
7. Prime Check — **EASY**
8. GCD and LCM — **EASY**
9. Fibonacci Number — **EASY**
10. Decimal to Binary — **EASY**

### 2. Arrays — Fundamentals (12 problems)
Category: `ARRAYS`

1. Array Sum — **EASY**
2. Array Minimum and Maximum — **EASY**
3. Reverse Array — **EASY**
4. Second Largest Distinct Element — **EASY**
5. Frequency of Array Elements — **EASY**
6. Move Zeroes — **EASY**
7. Move Zeroes 2 — **MEDIUM**
8. Rotate Array — **EASY**
9. Rotate Array 2 — **MEDIUM**
10. Merge Sorted Arrays — **EASY**
11. Missing Number in Sequence — **EASY**
12. Maximum Subarray Sum — **MEDIUM**

### 3. Strings (12 problems)
Category: `STRINGS`

1. Reverse String — **EASY**
2. Valid Palindrome — **EASY**
3. Count Vowels — **EASY**
4. Character Frequency — **EASY**
5. First Non-Repeating Character — **EASY**
6. Valid Anagram — **EASY**
7. Longest Common Prefix — **EASY**
8. String Compression — **MEDIUM**
9. Group Anagrams — **MEDIUM**
10. Compare Version Numbers — **MEDIUM**
11. Longest Palindromic Substring — **MEDIUM**
12. String to Integer Parsing — **MEDIUM**

### 4. Two Pointers (10 problems)
Category: `TWO_POINTERS`

1. Two Sum in Sorted Array — **EASY**
2. Pair With Given Difference — **EASY**
3. Remove Duplicates from Sorted Array — **EASY**
4. Remove Duplicates from Sorted Array 2 — **MEDIUM**
5. Valid Palindrome After One Deletion — **EASY**
6. Sort Colors — **MEDIUM**
7. Container With Most Water — **MEDIUM**
8. Three Sum — **MEDIUM**
9. Four Sum — **MEDIUM**
10. Trapping Rain Water — **HARD**

### 5. Sliding Window (10 problems)
Category: `SLIDING_WINDOW`

1. Maximum Sum of K Consecutive Elements — **EASY**
2. Longest Substring Without Repeating Characters — **MEDIUM**
3. Minimum Size Subarray Sum — **MEDIUM**
4. Find All Anagram Windows — **MEDIUM**
5. Permutation in String — **MEDIUM**
6. Longest Repeating Character Replacement — **MEDIUM**
7. Fruits Into Baskets — **MEDIUM**
8. Sliding Window Maximum — **HARD**
9. Minimum Window Substring — **HARD**
10. Subarrays With At Most K Distinct Values — **HARD**

### 6. Hashing and Prefix Sums (11 problems)
Category: `HASHING`

1. Two Sum — **EASY**
2. Two Sum 2 — **MEDIUM**
3. Contains Duplicate — **EASY**
4. Majority Element — **EASY**
5. Range Sum Queries — **EASY**
6. Subarray Sum Equals K — **MEDIUM**
7. Longest Consecutive Sequence — **MEDIUM**
8. Product of Array Except Self — **MEDIUM**
9. Subarrays Divisible by K — **MEDIUM**
10. Longest Zero Sum Subarray — **MEDIUM**
11. Count Submatrices With Target Sum — **HARD**

### 7. Sorting and Binary Search (11 problems)
Category: `SEARCH_SORT`

1. Bubble Sort — **EASY**
2. Selection Sort — **EASY**
3. Merge Sort — **MEDIUM**
4. Quick Sort — **MEDIUM**
5. Binary Search — **EASY**
6. First and Last Position of Target — **MEDIUM**
7. Search in Rotated Sorted Array — **MEDIUM**
8. Find Peak Element — **MEDIUM**
9. Minimum Capacity to Ship Packages — **MEDIUM**
10. Koko Eating Bananas — **MEDIUM**
11. Median of Two Sorted Arrays — **HARD**

### 8. Stacks and Queues (10 problems)
Category: `STACK_QUEUE`

1. Valid Parentheses — **EASY**
2. Next Greater Element — **EASY**
3. Implement Queue Using Two Stacks — **EASY**
4. Implement Stack Using Two Queues — **EASY**
5. Min Stack — **MEDIUM**
6. Evaluate Reverse Polish Notation — **MEDIUM**
7. Daily Temperatures — **MEDIUM**
8. Asteroid Collision — **MEDIUM**
9. Largest Rectangle in Histogram — **HARD**
10. Basic Calculator — **HARD**

### 9. Linked Lists (10 problems)
Category: `LINKED_LIST`

1. Reverse Linked List — **EASY**
2. Middle of Linked List — **EASY**
3. Merge Two Sorted Linked Lists — **EASY**
4. Detect Linked List Cycle — **EASY**
5. Remove Nth Node From End — **MEDIUM**
6. Detect Cycle Entry — **MEDIUM**
7. Add Two Numbers as Lists — **MEDIUM**
8. Reorder Linked List — **MEDIUM**
9. Copy List With Random Pointer — **MEDIUM**
10. Reverse Nodes in K Group — **HARD**

### 10. Trees and BST (12 problems)
Category: `TREES`

1. Binary Tree Height — **EASY**
2. Inorder Traversal — **EASY**
3. Preorder Traversal — **EASY**
4. Postorder Traversal — **EASY**
5. Level Order Traversal — **MEDIUM**
6. Binary Tree Diameter — **MEDIUM**
7. Validate Binary Search Tree — **MEDIUM**
8. Lowest Common Ancestor — **MEDIUM**
9. Construct Tree From Traversals — **MEDIUM**
10. Kth Smallest Element in BST — **MEDIUM**
11. Path Sum III — **MEDIUM**
12. Serialize and Deserialize Binary Tree — **HARD**

### 11. Heaps and Greedy (10 problems)
Category: `GREEDY_HEAP`

1. Kth Largest Element — **MEDIUM**
2. Top K Frequent Elements — **MEDIUM**
3. Connect Ropes at Minimum Cost — **MEDIUM**
4. Activity Selection — **EASY**
5. Non Overlapping Intervals — **MEDIUM**
6. Jump Game — **MEDIUM**
7. Gas Station Circuit — **MEDIUM**
8. Task Scheduler — **MEDIUM**
9. Merge K Sorted Lists — **HARD**
10. Minimum Number of Refueling Stops — **HARD**

### 12. Recursion and Backtracking (10 problems)
Category: `BACKTRACKING`

1. Recursive Digit Sum — **EASY**
2. Generate All Subsets — **MEDIUM**
3. Subsets With Duplicates — **MEDIUM**
4. Generate Permutations — **MEDIUM**
5. Combination Sum — **MEDIUM**
6. Generate Balanced Parentheses — **MEDIUM**
7. Palindrome Partitioning — **MEDIUM**
8. Word Search — **MEDIUM**
9. N Queens — **HARD**
10. Sudoku Solver — **HARD**

### 13. Graphs (14 problems)
Category: `GRAPHS`

1. Breadth First Search Traversal — **EASY**
2. Depth First Search Traversal — **EASY**
3. Flood Fill — **EASY**
4. Number of Islands — **MEDIUM**
5. Number of Connected Components — **MEDIUM**
6. Detect Cycle in Undirected Graph — **MEDIUM**
7. Detect Cycle in Directed Graph — **MEDIUM**
8. Course Schedule Feasibility — **MEDIUM**
9. Lexicographically Smallest Topological Order — **MEDIUM**
10. Shortest Path in Unweighted Graph — **MEDIUM**
11. Dijkstra Shortest Paths — **MEDIUM**
12. Bellman Ford Shortest Paths — **MEDIUM**
13. Kruskal Minimum Spanning Forest — **MEDIUM**
14. Word Ladder Length — **HARD**

### 14. Dynamic Programming (15 problems)
Category: `DYNAMIC_PROGRAMMING`

1. Climbing Stairs — **EASY**
2. House Robber — **MEDIUM**
3. House Robber 2 — **MEDIUM**
4. Coin Change Minimum Coins — **MEDIUM**
5. Unique Paths — **MEDIUM**
6. Unique Paths With Obstacles — **MEDIUM**
7. Minimum Path Sum — **MEDIUM**
8. Longest Increasing Subsequence — **MEDIUM**
9. Longest Common Subsequence — **MEDIUM**
10. 0-1 Knapsack — **MEDIUM**
11. Partition Equal Subset Sum — **MEDIUM**
12. Decode Ways — **MEDIUM**
13. Word Break — **MEDIUM**
14. Edit Distance — **HARD**
15. Burst Balloons — **HARD**

### 15. Bit Manipulation (10 problems)
Category: `BIT_MANIPULATION`

1. Count Set Bits — **EASY**
2. Power of Two — **EASY**
3. Single Number — **EASY**
4. Missing Number Using XOR — **EASY**
5. XOR of Range — **EASY**
6. Reverse 32 Bits — **EASY**
7. Two Unique Numbers — **MEDIUM**
8. Bitwise AND of Range — **MEDIUM**
9. Total Hamming Distance — **MEDIUM**
10. Maximum XOR of Two Numbers — **HARD**

### 16. Advanced Data Structures (10 problems)
Category: `ADVANCED_DSA`

1. Trie Prefix Search — **MEDIUM**
2. Wildcard Word Dictionary — **MEDIUM**
3. Disjoint Set Union Queries — **MEDIUM**
4. Redundant Connection — **MEDIUM**
5. Fenwick Tree Range Queries — **MEDIUM**
6. LRU Cache Simulation — **MEDIUM**
7. Median From Data Stream — **HARD**
8. Segment Tree Range Minimum — **HARD**
9. Segment Tree With Lazy Range Updates — **HARD**
10. Largest Rectangle in Binary Matrix — **HARD**

### 17. Matrices and Grids (10 problems)
Category: `MATRICES`

1. Matrix Transpose — **EASY**
2. Rotate Square Matrix — **MEDIUM**
3. Spiral Matrix Traversal — **MEDIUM**
4. Set Matrix Zeroes — **MEDIUM**
5. Search Sorted Matrix — **MEDIUM**
6. Count Square Submatrices of Ones — **MEDIUM**
7. Island Perimeter — **EASY**
8. Surrounded Regions — **MEDIUM**
9. Shortest Path in Binary Matrix — **MEDIUM**
10. Maximal Rectangle — **HARD**

## Acceptance criteria for Cursor deliverable

- [ ] All 187 entries have original high-quality statements, constraints, explicit stdin/stdout, sample explanation and tags.
- [ ] Correctly classified EASY/MEDIUM/HARD with versioned beginner/advanced variants.
- [ ] Reference oracle passes every public/hidden case; checkers deterministic.
- [ ] No hidden test contents or answer oracles committed to public git; secret scan clean.
- [ ] Safe authenticated CLI supports dry-run, batch apply, resume/idempotency, API verification and rollback planning.
- [ ] 17 accessible ordered concept bundles with canonical backend IDs and accurate totals.
- [ ] API integration tests pass, including hidden-test redaction and authorization.
- [ ] Staging judge tests show accepted solutions pass and representative incorrect solutions fail.
- [ ] Report actual count by level, category and imported status, errors, and review status.
- [ ] No production writes until staging is verified and user approval recorded.
