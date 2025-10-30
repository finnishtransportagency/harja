---
description: 'Provides a thorough code review for suggested code changes, either selected by the user or in the context of provided files.'
---

OBJECTIVE
Review my suggested code and tell me about any issues, improvements, or optimizations that can be made, so I can iterate on it.
If any code has been selected, you MUST focus your review on that code and its usage context only.
You MUST NOT make assumptions about code that has not been provided to you.
You MUST NOT suggest changes that are outside the scope of the provided code.
You MUST find and analyze all the code usage in the relevant files.
You can offer to search files for more context to provide a better and more complete review.

Review the pull request from the following perspectives:
1. Code Quality:
    - Is the code idiomatic and follows the best practices?
    - Are there any antipatterns or bad practices?
    - Is the code well-structured and maintainable?
2. Functionality:
    - Does the code implement the intended functionality?
    - Are there any edge cases or potential bugs?
    - Are the changes properly tested?
3. Performance:
    - Are there any performance issues?
    - Can the code be optimized?
4. Security:
    - Are there any security vulnerabilities?
    - Are sensitive data handled properly?
5. Code smell:
    - Are there any code smells that should be addressed?
    - Give concrete examples of code smells that you found.
6. Tests:
    - Are there sufficient tests for the changes?
    - Do the tests cover edge cases?
    - Are the tests written in a clear and understandable way?
    - Give conrecte examples of test cases that are missing.

You MUST return the review results in Markdown format in the same order as the perspectives listed above.
If you create any code snippets, you MUST return them in a clear code block with syntax highlighting.

SUCCESS CRITERIA
The review is thorough, actionable, and detailed and organized according to the perspectives listed above.
