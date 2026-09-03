# Prototype Brief: Ylläpitourakoiden työpöytä (MVP)

## Purpose
Design a dashboard homepage view for Harja maintenance contracts. The main purpose is to give the user a quick situation overview across relevant contracts and help them identify missing data, incomplete reporting, and urgent deadlines.

## Primary Users
- Urakanvalvoja: sees all own contracts in one view
- Urakoitsija: sees only own contract

## Main Problem
Important contract information is currently spread across multiple views in Harja. Users need to manually navigate different sections to understand what is incomplete, overdue, or requires action. This makes it difficult to get an overall view, especially when the user is responsible for multiple contracts.

## Main Goal
The dashboard should help the user understand at a glance:
- what needs attention now
- which contracts have missing or incomplete data
- which deadlines are approaching or overdue
- where to go next in Harja to fix the issue

## MVP Principles
- The dashboard is mainly for overview and navigation
- Users do not perform major actions directly on the dashboard
- Clicking cards or items takes the user to the relevant detailed Harja subview
- The view should support filtering by one selected contract
- The layout should support scanning across multiple contracts for supervisors

## Key UX Principles
- Show the most important issues clearly
- Layout must be accessible (WCAG AA level standard) - don't use just color to display important information.
- Avoid clutter
- Support fast scanning and prioritization
- Highlight incomplete or outdated data
- UI Language is Finnish.

## Main Sections

### 1. Calendar / Deadline Reminders
This is one of the most important sections.

Show only items that need attention:
- overdue = red
- upcoming deadline = yellow
- completed or OK items are hidden

Example reminders:
- general information must be completed by a certain date
- review instructions at the start of the maintenance season
- add patching targets at the start of the season
- report actuals by deadline
- fill penalties and bonuses before end of contract period
- send POT forms to YHA by deadline
- other scheduled events

### 2. Paikkauskohteet Status
Show the reporting and quality status of patching targets.

Include:
- unprocessed orders
- reporting status:
  - not started
  - in progress
  - done
- missing data issues:
  - actuals missing
  - POT form missing
  - marked complete too early
  - actual price missing
  - road marking related issues
- price coverage / cost completeness for planned targets

Use counts and ratios where possible, for example:
- `5/220`
- `12 missing`
- `18 in progress`

### 3. POT Forms Status
Show POT form process status.

Include:
- not started
- in progress
- unprocessed
- not sent to YHA
- invalid / erroneous

Show counts and ratios where possible.

### 4. Recent Changes
Show a simple list of important recent changes in contract data.

Include:
- what changed
- when it changed

### 5. Cost / Status Graph
Show a simple visual summary by contract, for example a bar chart.

Possible use:
- compare realized euro amounts across contracts
- help identify if one contract appears to be missing cost entries

### 6. Guides
Show links to instructions.

### 7. Current Topics
Show current informational items such as:
- news
- clinics
- Harja updates

## Interaction Requirements
- Cards and list items should be clickable.
- Clicking should navigate the user to the correct detailed subview in Harja
- The dashboard itself should not contain heavy editing workflows
- Filtering by contract should be supported
- Role-based visibility must be supported:
  - supervisor sees all own contracts
  - contractor sees only own contract

## Visual Prioritization Rules
- Overdue = red
- Upcoming deadline = yellow
- Completed / OK = hidden from the calendar section, is needed to display completed items = green
- Counts and ratios should be visible where useful
- The hierarchy should guide the eye first to urgent and incomplete items

## Suggested Layout Direction
A clean enterprise dashboard with:
- a prominent calendar / reminders area
- status cards for key process areas
- a recent changes list
- a small graph area
- side or lower sections for guides and current topics

The dashboard should feel informative, scannable, and actionable without becoming a data-heavy reporting page.

## Constraints / Design Notes
- Some deadlines can be derived from existing system data, some cannot
- Guide links may need to be manually maintained
- Performance implications for cross-contract dashboard views are not yet known
- MVP should focus on high-value overview components first
