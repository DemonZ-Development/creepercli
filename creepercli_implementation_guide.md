# CreeperCLI Implementation Guide for AI Agent

This document serves as a comprehensive guide for an AI agent tasked with developing the CreeperCLI project. It integrates the original 10-Phase Development Plan with detailed assessments and recommendations to ensure a secure, robust, and maintainable final product. The agent should follow these phases sequentially, paying close attention to the recommendations provided for each stage.

## Phase 1: Project Scaffolding & Architecture Setup

**Original Plan Focus:** Establishing the codebases, repositories, and communication baseline.

*   Initialize the Java Spigot/Paper plugin project (Maven/Gradle setup).
*   Initialize the Node.js CLI project (`npm init`, `package.json` setup).
*   Define the core Newline-Delimited JSON (NDJSON) message schemas (Request, Response, Error frames).
*   Create the basic TCP Server socket in Java and the TCP Client in Node.js.

**Original Deliverable:** A Node.js CLI can connect to the Java plugin's custom TCP port, and they can exchange a basic JSON "ping/pong" message.

**Recommendations for AI Agent:**

Explicitly define the **technology stack versions** for Java (e.g., Java 17, 21), Maven/Gradle, and Node.js to ensure consistency and avoid compatibility issues. Implement a detailed **error handling strategy** for the initial TCP communication, including specific error codes or types for common connection issues, beyond just basic JSON error frames.

## Phase 2: Authentication & Session Security

**Original Plan Focus:** Locking down the TCP port so only authorized users can access it.

*   Implement the `creepercli-users.yml` configuration for storing bcrypt-hashed passwords.
*   Build the authentication handshake (Client sends password -> Server verifies via bcrypt).
*   Implement TOTP (2FA) integration using a Java security library (generating QR codes for setup, verifying 6-digit codes).
*   Implement session tokens (issuing, tracking, and expiring them after 15 minutes of inactivity).
*   Build the Fail2Ban logic (tracking failed attempts by IP and temporarily banning IPs after 3 failures).

**Original Deliverable:** A user must pass a valid password and 2FA code via the CLI to get a session token; invalid attempts trigger IP bans.

**Recommendations for AI Agent:**

Implement **rate limiting on authentication attempts** at the server level, independent of Fail2Ban, to mitigate brute-force attacks more effectively. Ensure **secure storage and transmission of TOTP secrets** during the QR code generation and setup process. Explicitly outline the **session token invalidation mechanism** (e.g., on password change, forced logout) to enhance security.

## Phase 3: The Hardened Sandbox (Path Sanitizer)

**Original Plan Focus:** Building the impenetrable jail that prevents directory traversal and symlink escapes.

*   Implement the `PathSanitizer` utility in Java.
*   Code the logic for resolving real paths (`toRealPath()`) for existing files to defeat symlinks.
*   Code the fallback logic for non-existent files (resolving the nearest existing parent, verifying it is inside the server root, then appending the new file path).
*   Write extensive automated tests (JUnit) throwing every conceivable malicious path (`../`, absolute paths, encoded paths, deep symlinks) at the sanitizer to ensure it blocks them all.

**Original Deliverable:** The path jail is mathematically and practically proven to be secure against file system traversal.

**Recommendations for AI Agent:**

Consider **edge cases related to different operating systems** (Windows path separators, case insensitivity) if cross-platform compatibility is a future goal. Document the **assumptions about the server root directory** and how it is configured and enforced, as this is fundamental to the sanitizer's effectiveness. Regular security audits of the `PathSanitizer` implementation should be a recurring task.

## Phase 4: Basic Filesystem Operations & CLI REPL

**Original Plan Focus:** Navigating the server like a standard terminal.

*   Build the interactive REPL (Read-Eval-Print Loop) in the Node.js CLI using `readline` or `inquirer` (handling command history, arrow keys, Ctrl+C).
*   Implement the `CommandRouter` in Java to route incoming JSON actions to the correct handler.
*   Implement core navigation commands: `pwd`, `ls` (with `-l` and `-a` flags), `cd`, and `tree`.
*   Implement basic read commands: `cat`, `head`, `tail` (static, not streaming yet), and `wc`.

**Original Deliverable:** A user can connect via the CLI and browse the Minecraft server's directory structure and read text files.

**Recommendations for AI Agent:**

For `head` and `tail` commands, prioritize **streaming capabilities**, especially for large log files, to improve user experience and responsiveness. Ensure that `ls -l` accurately displays **file permissions** (read, write, execute) in a standard format. Implement **command history persistence** across CLI sessions for the REPL to enhance usability for developers.

## Phase 5: Text Editing & The Local Editor Workflow

**Original Plan Focus:** The seamless "pull, edit locally, push" experience.

*   Implement the `edit` command workflow:
    1.  CLI requests the file from the server.
    2.  CLI writes the content to a temporary local file.
    3.  CLI spawns the user's local `$EDITOR` (e.g., nano, vim, VS Code) via Node `child_process`.
    4.  CLI detects when the editor process closes.
    5.  CLI reads the temp file, checks if content changed, and pushes the new content back to the server.
*   Implement basic file manipulation commands: `touch`, `mkdir`, `rm`, `cp`, `mv`.

**Original Deliverable:** A developer can run `edit server.properties`, have it open in their local VS Code, make changes, save, and see the changes reflected on the server instantly.

**Recommendations for AI Agent:**

To prevent data loss and ensure a robust editing experience, it is crucial to consider **concurrency and locking mechanisms**. If multiple users attempt to `edit` the same file simultaneously, a basic locking system or at least a clear warning should be implemented to prevent conflicting changes. Furthermore, for **file change detection robustness**, beyond a simple hash comparison, exploring a more sophisticated diffing algorithm could provide users with more granular feedback on what specifically changed, especially in scenarios where the local editor might auto-save. Finally, **temporary file security** is paramount; ensure that all temporary local files created during the `edit` workflow are stored in a secure, isolated location and are reliably cleaned up after the editing session, regardless of whether changes were saved or not.

## Phase 6: Advanced Search & Binary Transfers (SCP-Style)

**Original Plan Focus:** Searching data and transferring large files/binary blobs.

*   Implement `grep` (recursive, case-insensitive, line numbers) and `find` (glob pattern matching) on the Java side.
*   Develop the chunked binary transfer protocol (Base64 encoding in 64KB chunks).
*   Implement `cpush` (upload local file to server) and `cpull` (download server file to local).
*   Implement `csync` (compare local and remote directories via checksums and sync differences).

**Original Deliverable:** A user can upload a new plugin `.jar` from their local PC to the server's `plugins/` folder, or download a world backup to their local machine.

**Recommendations for AI Agent:**

For **large file transfer resilience**, especially with `cpush` and `cpull`, it is highly recommended to implement mechanisms for resuming interrupted transfers and verifying data integrity (e.g., using checksums) after completion. This is particularly important for large files or unstable network conditions. While `csync` already mentions checksums, extending this to `cpush` and `cpull` would be beneficial. Regarding **resource management for `grep` and `find`**, given these operations can be resource-intensive on the Java side, consider adding options to limit search depth, file size, or CPU usage to prevent performance degradation on the server. Finally, implementing **progress indicators** for binary transfers (`cpush`, `cpull`, `csync`) in the CLI would provide valuable feedback to the user, especially during lengthy operations.

## Phase 7: Bukkit Integration & Safe Console Execution

**Original Plan Focus:** Interacting with the Minecraft server itself, safely.

*   Implement the default-deny `exec-allowlist` logic in the Java config.
*   Build the `BukkitBridge`: A component that takes an `exec` request from the network thread, schedules it on the Minecraft main thread via `Bukkit.getScheduler().runTask()`, captures the command output, and returns it to the network thread.
*   Implement the `exec` command in the CLI.
*   Add command shortcuts: `say` and `restart`.

**Original Deliverable:** A user can run `exec whitelist add Steve` from their terminal, and it executes safely on the server without throwing async exceptions.

**Recommendations for AI Agent:**

To further refine the security model, consider implementing **granular permissions for `exec` commands**. Beyond a simple allowlist, this could involve defining permissions based on user roles, specific command arguments, or even context-sensitive rules. While this might be an advanced feature for a later iteration, it's worth considering in the architectural design. Ensuring **consistent command output formatting** from the `BukkitBridge` is crucial for the CLI to parse and display results effectively, especially for commands that produce extensive or structured output. Lastly, implementing a **timeout mechanism for `exec` commands** would prevent long-running or hung commands from blocking the server's main thread or the CLI session, improving overall system stability and responsiveness.

## Phase 8: Live Monitoring & Log Streaming

**Original Plan Focus:** Real-time server observation.

*   Implement `stats` (CPU, RAM, Disk usage via Java `OperatingSystemMXBean`).
*   Implement `tps` (Ticks Per Second) by hooking into Paper's API or calculating tick durations.
*   Implement the live `tail -f` command: Java opens `logs/latest.log`, watches for new lines using Java `WatchService`, and streams them over the TCP socket instantly.
*   Add `--grep` filtering to the live log stream.
*   Implement `top` (a live-updating dashboard in the CLI that refreshes every 2 seconds).

**Original Deliverable:** A developer can watch the server console live in their terminal and monitor TPS/RAM while testing plugins in-game.

**Recommendations for AI Agent:**

For `top` and other live-updating dashboards, allowing users to **configure refresh rates** would provide flexibility, enabling them to balance real-time monitoring with potential server load. While live monitoring is crucial, considering the addition of **historical data capabilities for `stats` and `tps`** would be a valuable future enhancement, perhaps by storing snapshots or integrating with a time-series database for trend analysis. Furthermore, it is important to ensure that the `tail -f` implementation gracefully handles **log rotation events** (e.g., `logs/latest.log` being renamed and a new one created) to maintain continuous log streaming without interruption.

## Phase 9: Rate Limiting, Auditing & Penetration Testing

**Original Plan Focus:** Final security hardening before public release.

*   Implement the token-bucket Rate Limiter (max 10 commands/second per session).
*   Build the append-only Audit Logger (writing timestamp, IP, user, and command to `creepercli-audit.log` with 10MB rotation).
*   Conduct rigorous penetration testing:
    *   Attempt path traversal via encoded strings.
    *   Attempt to bypass the exec allowlist.
    *   Flood the server with commands to test rate limiting.
    *   Drop connections abruptly to ensure no memory/thread leaks occur on the Java side.

**Original Deliverable:** The plugin is hardened against abuse, crashes, and bypasses.

**Recommendations for AI Agent:**

To further bolster security, consider integrating **automated security testing tools** into the CI/CD pipeline (Phase 10) to continuously scan for common vulnerabilities, rather than relying solely on manual penetration testing. Beyond just logging, implementing a mechanism to **alert administrators on audit log anomalies** (e.g., via email, Discord webhook) would provide proactive threat detection for suspicious patterns or high volumes of failed attempts. Finally, for a project with such a strong security focus, engaging with **external security experts for a professional penetration test or code audit** before public release would provide an independent and thorough validation of its security posture.

## Phase 10: Documentation, CI/CD & Public Release

**Original Plan Focus:** Packaging, distributing, and supporting the tool.

*   Set up GitHub Actions to automatically compile the Java `.jar` on release tags.
*   Set up GitHub Actions to automatically publish the NPM package to the public registry.
*   Write comprehensive documentation (Quickstart, Configuration, Security Best Practices, Command Reference).
*   Emphasize in docs that the server *must* run as a non-root OS user and remote connections *must* use SSH tunnels.
*   Publish the Java plugin to Modrinth and Hangar.

**Original Deliverable:** CreeperCLI v1.0.0 is publicly available, installable via `npm i -g creeper-cli`, and fully documented.

**Recommendations for AI Agent:**

To ensure clarity and maintainability, explicitly stating the use of **Semantic Versioning** for releases will provide a clear understanding of changes between versions for users and contributors. Including clear **contribution guidelines** (e.g., in a `CONTRIBUTING.md` file) will encourage community involvement and help maintain code quality. A fundamental aspect of any open-source project is the **license choice**; clearly defining the software license under which CreeperCLI will be released is crucial. Finally, establishing a clear **user feedback mechanism** (e.g., GitHub Issues, Discord server) will be vital for bug reporting, feature suggestions, and ongoing support post-release.
