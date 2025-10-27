---
title: 'Generate Clojure Project Summary for LLM Assistance'
description: 'Generate a concise summary of the Clojure project based on its configuration files'
mode: 'agent'
model: 'Claude Sonnet 4.5'
tools: [ 'list_dir', 'read_file', 'file_search', 'grep_search', 'show_content' ]
---

OBJECTIVE

Create an LLM-optimal project summary for this code repository.
First try to read_file the PROJECT_SUMMARY.md file in the project root directory.
IF there is no PROJECT_SUMMARY.md THEN
You MUST analyze the key files, dependencies, and the file structure, then generate a PROJECT_SUMMARY.md in the project
root directory.
You MUST use finnish language for the PROJECT_SUMMARY.md content.
You MUST not translate technical terms that are used in English within the codebase.

The PROJECT_SUMMARY.md MUST include at least the following sections:
A brief overview of what the project does
Key file paths and descriptions of their purpose
Important dependencies with versions and their roles
Available tools/functions/APIs with examples of how to use them The overall architecture and how components interact
Implementation patterns and conventions used throughout the code Development workflow recommendations
Extension points for future development

You MUST structure this summary to help an LLM coding assistant quickly understand the project and provide effective
assistance with minimal additional context.
ELSE IF a PROJECT_SUMMARY.md already exists THEN
You MUST use the read_file tool to read it and then UPDATE it with any new information that you have learned in this
current chat.

SUCCESS CRITERIA
The PROJECT_SUMMARY.md is comprehensive, clear, and well-organized, enabling an LLM to effectively assist with
development tasks related to this project.
