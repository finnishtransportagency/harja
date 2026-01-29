GROUND RULES
YOU MUST use Finnish language for all code, comments, produced documentation and while chatting.
IF a PROJECT_SUMMARY.md file is found THEN YOU MUST read it and use it as reference for all future responses in this chat.
Doing it right is better than doing it fast. You are not in a rush. NEVER skip steps or take shortcuts.
DO NOT translate common technical terms into Finnish if they are written in english in our codebase or in the documentaiton.
Use leiningen for managing our Clojure dependencies. Dependencies are defined in files `project.clj` and `profiles.clj` in the root of the project.
Use Figwheel-main for building our ClojureScript code.
Use npm for managing our JavaScript dependencies.
Use GitHub Actions for CI/CD.
Use PostgreSQL as database.
Database migrations are managed with flyway and the migration files are located in the
`tietokanta/src/main/resources/db/migration/` directory.
Cypress is used for end-to-end testing and tests are located in the `cypress/e2e/` directory.

# Communication guidelines
* Keep your answers short, focused, and actionable. Do not include unnecessary information.
* YOU MUST point out any potential risks, bad ideas or mistakes, NEVER agree just to please.

# Software design
* Favor simplicity and clarity over cleverness. Avoid over-engineering.
* "You aren't gonna need it" (YAGNI). Avoid adding unnecessary features or changes.
* Design software changes for extensibility and flexibility where it makes sense

# Check if a linux native terminal tool/command is installed
IMPORTANT: DO NOT check npm packages or other non-native tools.
**CRITICAL: Always append `; echo ""` to terminal commands when using `run_in_terminal` when checking for a tool existence.**

IF you plan to use native linux terminal tools for anything, YOU MUST check that the tool is installed in the local dev 
environment by running EXACTLY `which <tool> > /dev/null 2>&1; echo $?` by using run_in_terminal or similar method.
After running the command, check the exit code:
* IF exit code is 0, the tool is installed
* IF exit code is non-zero, the tool is not installed

## Example: 'cpulimit' tool is installed
YOU: which <tool> > /dev/null 2>&1; echo $?
TERMINAL OUTPUT: 0
YOU: The 'cpulimit' tool is installed at /usr/bin/cpulimit, so I can use it in this environment.

## Example: 'cpulimit' tool is not installed
YOU: which <tool> > /dev/null 2>&1; echo $?
TERMINAL OUTPUT: 1
YOU: The 'cpulimit' tool is not installed in this environment, so I cannot use it. Please install it first. I will wait for you to confirm the installation is done before continuing.

IMPORTANT: IF the tool is not installed, wait for the USER to install it. DO NOT attempt to use the tool if it is not installed.
IMPORTANT: If USER does not install the tool, you MUST use alternative tools/commands/methods.


# Systematic Debugging Process

⚠️ CRITICAL RULE: REPRODUCTION BEFORE FIXES ⚠️
YOU ARE ABSOLUTELY FORBIDDEN from making ANY code changes BEFORE you have successfully reproduced the issue.

READ THIS CAREFULLY:
- ❌ DO NOT make code changes without reproduction first  
- ❌ DO NOT say "let me fix this" without reproduction first
- ❌ DO NOT assume you know the fix without reproduction first
- ✅ DO reproduce the issue consistently first using available tools
- ✅ DO run tests, commands or ask user to provide an error message to see the actual error
- ✅ DO verify the issue exists before ANY fix attempt

VIOLATION OF THIS RULE = COMPLETE FAILURE OF THE TASK

YOU MUST ALWAYS find the root cause of an issue you are debugging, by using root cause analysis techniques (RCA).
YOU MUST NEVER fix a symptom or add a workaround instead of finding a root cause, even if it is faster.
YOU MUST NEVER attempt fixing before reproducing the issue.

IF the user reports a bug or issue, your FIRST response MUST include:
1. Acknowledgment that you will reproduce first
2. The specific tool/command you will use to reproduce

YOU MUST follow the following debugging framework for ANY technical issue:

## Phase 1: Root Cause Analysis (MANDATORY - BEFORE attempting ANY fixes)

⛔ STOP GATE: You CANNOT proceed to Phase 2, 3, or 4 until ALL items below are completed ⛔

MANDATORY CHECKLIST - You MUST explicitly confirm each item:
- [ ] **Reproduce Consistently**: Have you successfully reproduced the issue at least once using actual tools/commands?
  - If NO: STOP. Use tools that USER has asked to use or other tools to reproduce NOW
  - If YES: Document the exact steps and error output
  
- [ ] **Read Error Messages Carefully**: Have you read the COMPLETE error message/stack trace?
  - Don't skip past errors or warnings - they often contain valuable information
  - Quote the relevant error text in your response
  
- [ ] **Check Recent Changes**: Have you investigated what changed that could have caused the issue?
  - Git diff, recent commits, etc.
  - If applicable, document what changed

VERIFICATION: State explicitly: "I have completed Phase 1. The issue was reproduced with [tool/command]. The error is: [error message]."

WITHOUT this verification statement, you MUST NOT continue to Phase 2.

## Phase 2: Pattern Analysis
* **Find Working Examples**: Find similar working code in the codebase
* **Analyze Best Practices**: Consult the tooling/framework documentation for recommended practices, or utilize project's best practices
* **Compare Against References**: If implementing a pattern, read the reference implementation completely
* **Identify Differences**: What's different between working and broken code?
* **Understand Dependencies**: What other components/settings does this pattern require?

## Phase 3: Hypothesis and Testing
1. **Form a Single Hypothesis**: What do you think is the root cause? State it clearly and consisely
2. **Test Minimally**: Make the smallest possible change to test your hypothesis
3. **Verify Before Continuing**: Did your test work? IF not, form a new hypothesis - Do not add more fixes
4. **When You Don't Know**: Say "I don't understand X" rather than pretending to know - Do not make assumptions

## Phase 4: Implementation Rules
- ALWAYS have the simplest possible failing test case. If there's no test framework, it's ok to write a one-off test script.
- NEVER add multiple fixes at once
- NEVER claim to implement a pattern without reading it completely first
- ALWAYS test after each change
- IF your first fix doesn't work, STOP and re-analyze rather than adding more fixes
- ALWAYS test that the final fix is stable and does not reproduce the issue


# Coding practices
FOLLOW THESE BEST PRACTICES FOR CLOJURE DEVELOPMENT:
* Emphasize functional programming with pure functions and immutable data structures
* Prefer proper conditionals: use 'if' for binary choices, 'cond' for multiple conditions, and 'if-let'/'when-let' for
  binding and testing in one step
* Recommend threading macros (-> and ->>) to eliminate intermediate bindings and improve readability
* Suggest destructuring in function parameters for cleaner access to data structures
* Design functions to do one thing well and return useful values
* Use early returns with 'when' rather than deeply nested conditionals
* Track actual values instead of boolean flags where possible
* Emphasize REPL-driven development with small, incrementally tested steps
* Organize code with thoughtful namespace design and clear dependency management
* Use appropriate Clojure abstractions like multimethods, protocols, or spec where relevant

FOLLOW THESE BEST PRACTICES FOR CSS / LESS DEVELOPMENT:
* Use REM units for font sizes and spacing to ensure responsiveness, avoiding fixed pixel values
* Utilize variables for colors to maintain consistency
    * Color variables are stored in `dev-resources/less/vayla/colors.less`
* For typography, utilize `dev-resources/less/vayla/typography.less`

FOLLOW THESE BEST PRACTICES FOR SQL DEVELOPMENT:
* Always add columns: "luoja", "luotu", "muokkaaja", "muokattu", and if necessary "poistaja", "poistettu"
* Name tables and columns in Finnish
* Add a common prefix to all table names, like for example "pot2_" for Pot2 tables, or "lupaus_" for Lupaus tables
* When updating or adding data, ensure that the "luoja" and "luotu" or "muokkaaja" and "muokattu" columns are updated
  accordingly
    * For "luoja" and "muokkaaja", use the user ID `(SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')`
    * For "luotu" and "muokattu", use the current timestamp `CURRENT_TIMESTAMP`
* Follow the general PostgreSQL best practices
* Follow the "river formatting" style for SQL. Spaces should be used to line up the code so that the root keywords all end on the same character boundary. 
  This forms a river down the middle making it easy for the readers eye to scan over the code and separate the keywords from the implementation detail. 

FOLLOW THESE BEST PRACTICES FOR CYPRESS TEST DEVELOPMENT:
* Use `data-cy` attributes for selecting elements in tests instead of classes or IDs to avoid brittle tests
* Organize tests in the `cypress/e2e/` directory following the existing structure
* Avoid using hard waits or timeouts - prefer checking for element visibility, element state changes or wait for network requests
* Use `cy.intercept` for waiting for network requests instead of arbitrary waits
* Actively look for deprecated Cypress commands and suggest to replace them with their modern equivalents



