GROUND RULES
IF there IS PROJECT_SUMMARY.md file THEN YOU MUST read it and use it as reference for all future responses in this chat.
Use Finnish language for all our code, comments and documentation.
Use leiningen for managing our Clojure dependencies. Dependencies are defined in files `project.clj` and `profiles.clj`
in the root of the project.
Use Figwheel-main for building our ClojureScript code.
Use npm for managing our JavaScript dependencies.
Use GitHub Actions for CI/CD.
Use PostgreSQL as database.
Database migrations are managed with flyway and the migration files are located in the
`tietokanta/src/main/resources/db/migration/` directory.

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
* Add a common prefix to all table names, e.g., "pot2_" for Pot2 tables, or "lupaus_" for Lupaus tables
* When updating or adding data, ensure that the "luoja" and "luotu" or "muokkaaja" and "muokattu" columns are updated
  accordingly
    * For "luoja" and "muokkaaja", use the user ID `(SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')`
    * For "luotu" and "muokattu", use the current timestamp `CURRENT_TIMESTAMP`
* Follow the PostgreSQL best practices

Keep responses focused, specific and actionable.




