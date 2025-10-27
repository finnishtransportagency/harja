---
title: 'EXPERIMENTAL: Browser-Based Debugging of Harja ClojureScript Tuck Application State'
description: 'EXPERIMENTAL: Debug Tuck state of the Harja ClojureScript application using browser tools'
mode: 'agent'
tools: ['navigate_page','wait_for','evaluate_script','list_console_messages','select_page','navigate_page_history']
---

STRICT EXECUTION POLICY
You MUST ONLY use these tools: navigate_page, wait_for, evaluate_script, list_console_messages, select_page, navigate_page_history.
You MUST NOT invoke any other tool (explicitly DO NOT use take_snapshot or similar visual capture tools).
You MUST execute ONLY the exact JavaScript function calls listed below unless the user explicitly requests something else.
DO NOT invent, modify, wrap, or extend the provided function calls.
If a function prints to console, still run it exactly; do not attempt to restructure it to return data.

OBJECTIVE
Debug the Harja ClojureScript Tuck application state by evaluating approved scripts and interpreting console output.

APP REQUIREMENTS FOR DEBUG
1. The CLJS namespace harja.tyokalut.tuck-debug MUST be required in the target UI view.
2. The call (tuck-debug/tuck ...) MUST replace (tuck/tuck ...) in that view.

CRITICAL: YOU ARE ONLY ALLOWED TO RUN THESE EXACT SCRIPTS IN THE BROWSER CONSOLE:
A. harja.tyokalut.tuck_debug.pretty_print_current_state()
B. harja.tyokalut.tuck_debug.pretty_print_debugger_states()
P1. return !!(window.harja && window.harja.tyokalut && window.harja.tyokalut.tuck_debug);
P2. return typeof window.harja?.tyokalut?.tuck_debug?.pretty_print_current_state === 'function';
(Use probes only if A fails.)
NO OTHER JAVASCRIPT IS PERMITTED.

BEFORE running evaluate_script, CONFIRM the exact string matches one of the approved scripts above.
IMPORTANT: These functions print to console, they don't return values.
YOU MUST call list_console_messages immediately after evaluate_script.

WORKFLOW STEPS
1. Ask the user:
   a. Provide the full URL of the Harja UI view where tuck-debug is enabled.
   b. Provide a concise description of the issue or behavior of interest.
2. Use navigate_page to open the provided URL.
3. Use wait_for to ensure the page load completion (wait for ready state or an appropriate delay).
4. Run evaluate_script with EXACT script A:
   window.harja.tyokalut.tuck_debug.pretty_print_current_state()
5. Immediately call list_console_messages and inspect:
   a. If console shows {:state-index N ...} or similar -> tuck-debug active -> proceed.
   b. If result was null/undefined or no output -> run probe P1 then P2.
6. If probes show tuck-debug unavailable:
   a. Instruct user to:
    - Require harja.tyokalut.tuck-debug in the namespace.
    - Replace (tuck/tuck ...) with (tuck-debug/tuck ...).
    - Rebuild/reload page.
      b. Stop after giving remediation steps (do not fabricate state).
7. If active: Run evaluate_script with EXACT script A OR B:
   window.harja.tyokalut.tuck_debug.pretty_print_debugger_states()
8. Call list_console_messages again. Collect all state transition lines (each with :state-index, :event, :state).
9. Analyze:
    - Identify last state-index.
    - Note missing success events.
    - Highlight large diffs or nil fields if visible.
10. Provide a concise interpretation:
    - Current state index.
    - Sequence of events.
    - Potential problem patterns (e.g., event stops before expected *Onnistui event).
11. If user asks for further filtering or a re-run after interaction, repeat starting at step 4 only.
12. Terminate when analysis is delivered. Do NOT loop or add new commands.

ERROR / EDGE HANDLING
- If evaluate_script returns an exception: report it and do not retry with modified code.
- If console flooded: summarize, do not truncate silently; indicate count of entries summarized.
- Never guess missing state content.

OUTPUT STYLE WHEN REPORTING
- Summarize findings: Current state index, number of transitions, notable events, anomalies.
- Suggest next investigation only if clearly warranted (e.g., missing success event).

DO NOT call take_snapshot.
DO NOT fabricate JavaScript calls.
DO NOT alter or wrap the approved function calls.
DO NOT attempt DOM inspection beyond probes P1/P2.
DO NOT output raw internal tool payloads.

SUCCESS CRITERIA
You have successfully completed the task when:
You executed exactly the provided diagnostics scripts A OR B OR probes P1/P2.
You listed and interpreted console messages.
You provided a concise, actionable summary or remediation instructions if tuck-debug was inactive.
