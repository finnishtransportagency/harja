# Onboard to current branch changes

Familiarize yourself with the changes on the current git branch to continue development work.

## Instructions

1. **Setup**: Fetch the latest from origin and identify the current branch name. Do NOT check for uncommitted or unstaged changes. If on `develop`, note this and skip remaining steps.

2. **Review commit history**: List commits on this branch that are not in `origin/develop`.

3. **Get changed files summary**: Get a diff summary (`--stat`) first to understand the scope. For small changes (1-2 commits), go straight to full diff.

4. **Review the diffs**: Examine the actual code changes. For large diffs, prioritize source code over configs, tests, and generated files.

5. **Summarize changes**: Provide a concise summary of:
   - What feature/fix is being developed (use branch name pattern and commit messages for clues)
   - Key files and their purpose
   - Current state of the work (what's done, what might be in progress)

## Expected output

After gathering context, provide:

1. **Branch**: Current branch name and commits ahead of `origin/develop`
2. **Commits**: List of commits with brief descriptions
3. **Changed Files**: Categorized summary (new/modified/deleted) with file count
4. **Summary**: What this branch is implementing/fixing and current progress
5. **Ready to Continue**: Ask what the user wants to work on next

Keep the summary focused and actionable so we can quickly continue development.
