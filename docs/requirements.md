# Requirements

## Functional scope

Visitors see a topic-oriented landing page and can register/login. Registration creates a MEMBER account and session. All ten interest ratings, at least one goal, experience, collaboration, and activity are saved before viewing matches.

Members and community leaders can edit their name/bio, update their questionnaire, view ranked matches, inspect profiles, send/answer friend requests, remove friends, join/leave active communities, submit posts, comment on approved posts, and use private/group conversations. All visible actions invoke Java APIs with MySQL persistence.

Private messaging requires friendship when opening or sending. Group creators invite 1–19 active friends and are added automatically. All conversation reads/writes require participation. Suspended participants pause message sending; suspended accounts cannot use authenticated APIs.

Leaders inherit member behavior. Only the currently assigned leader of an active community may approve or reject its pending posts, and cannot moderate their own posts. Pending/rejected posts are excluded from normal feeds. Admins have no moderation override and no normal private-message reader.

Administrators search/view user summaries, suspend/reactivate non-admin accounts, create/edit/archive communities, assign leaders, inspect activity metadata, and view UTC daily reports. Statistics and reports come from the database, never frontend constants.

## Nonfunctional requirements

- Plain Java 21+ with built-in HTTP server and JDBC; MySQL 8.0.16+.
- HTML/CSS/vanilla JS modules; no frameworks or bundler.
- Understandable model → DAO → service → controller separation.
- Prepared statements, validated input, salted PBKDF2 hashes, HttpOnly sessions, server-side access rules.
- Accessible labels, visible focus, responsive layouts, and reduced-motion handling.
- Dark/violet/glass design with restrained motion and pointer-only custom cursor.
- Explicit loading, validation, conflict, empty, success and failure states.
- MySQL persistence for all application data; only short-lived authentication sessions live in Java memory.

## Acceptance and evidence

The acceptance criteria include compiling, schema/seed execution, each requested user/leader/admin workflow, responsive UI, role enforcement, and documentation alignment. Compilation/domain/database fixture checks pass. The exhaustive HTTP suite exists but requires the missing JDBC driver. Consult testing.md for the current verification boundary.
