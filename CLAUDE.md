# BootFleet Development Rules

## Maven Dependencies
Before adding any new Maven dependency (artifact not already in an existing pom.xml), use `mcp__claude_ai_Maven-versions` to look up the current latest stable version. Do not guess version numbers from training data.

## Java Code Exploration
Before reading more than 2 Java files to understand class structure, call hierarchy, or method signatures, use the `spoon-claude` skill. It returns targeted AST excerpts at a fraction of the token cost of full file reads.

## Formatting
After creating or editing any `.java` file, run `mvn spotless:apply -pl <module> --no-transfer-progress` before staging or committing.
