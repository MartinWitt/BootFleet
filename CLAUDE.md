# BootFleet Development Rules

## Maven Dependencies
Before adding any new Maven dependency (artifact not already in an existing pom.xml), use `mcp__claude_ai_Maven-versions` to look up the current latest stable version. Do not guess version numbers from training data.

## Java Code Exploration
Before using Grep, Glob, or reading any Java files to answer questions about class structure, call hierarchy, method signatures, annotation usage, or symbol locations, use the `spoon-claude` skill. It returns targeted AST excerpts at a fraction of the token cost of file reads or grep searches. Only fall back to Grep/Read for Java if spoon-claude cannot answer the question.

## Formatting
After creating or editing any `.java` file, run `mvn spotless:apply -pl <module> --no-transfer-progress` before staging or committing.
