# FoliaSkyblock Project Instructions

**STRICT RULE: ONLY MAKE EDITS IN THE USER FOLDER**

The **only** directory where any file reads or edits are permitted for the FoliaSkyblock project is the permanent location in the user's folder:

**`C:\Users\CJ\IdeaProjects\FoliaSkyblock`**

- **NEVER** read from, write to, edit, or list files inside any Grok worktree such as `C:\Users\CJ\.grok\worktrees\ideaprojects-foliaskyblock\...` (or any other path under `.grok/worktrees`).
- **NEVER** use relative paths (e.g. `src/main/java/...`, `pom.xml`, `.`) for project source, resources, or docs. These would resolve to a temporary worktree in the current agent context.
- **ALL** tool calls involving project files **MUST** use the full absolute path under `C:\Users\CJ\IdeaProjects\FoliaSkyblock`, for example:
  - `C:\Users\CJ\IdeaProjects\FoliaSkyblock\pom.xml`
  - `C:\Users\CJ\IdeaProjects\FoliaSkyblock\src\main\java\...`
- For `run_terminal_command`, always start commands with `cd /d "C:\Users\CJ\IdeaProjects\FoliaSkyblock"` (or use `-f` / full paths for mvn, etc.) so the shell operates on the user folder copy.
- This permanent directory is the canonical source of truth. It is what the user's IDE (IntelliJ) sees directly.
- Grok worktree directories are temporary/ephemeral isolation copies only. Changes made there are not wanted.

**Project Root (for reference):**
`C:\Users\CJ\IdeaProjects\FoliaSkyblock`

**Working on FoliaSkyblock:**
- This is a Minecraft Folia server plugin (Maven + Java).
- Use Folia API schedulers (RegionScheduler / GlobalRegionScheduler / AsyncScheduler) instead of legacy Bukkit scheduler where possible.
- Follow existing patterns in database/ (DAO + manager delegation, no direct JDBC outside DAOs), managers, GUIs, and commands.
- After edits, prefer `mvn clean compile` (or verify via build in permanent dir) to check for errors.
- Keep changes focused; large refactors should be planned.

This rule is mandatory. All agents and sessions working on FoliaSkyblock must obey it. The user's explicit requirement is "we only want to make edits in the user folder".
