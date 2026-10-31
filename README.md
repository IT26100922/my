# IT Friend Finder

## Overview

A complete academic application for IT enthusiasts to find compatible friends, join topic communities, publish moderated posts, and collaborate through private and group conversations. The application uses plain Java OOP, JDBC, MySQL, and HTML/CSS/vanilla JavaScript.

**Verification status:** Java compilation and 286 domain checks pass. PostgreSQL schema and seed files are provided in `database/schema.postgresql.sql` and `database/seed.postgresql.sql`. Full JDBC/API workflow verification requires the PostgreSQL JDBC driver and a configured PostgreSQL instance.

## Features

- Registration, login, logout, salted password hashing, expiring server-side sessions.
- Five-step questionnaire and transparent weighted friend/community recommendations.
- Incoming/outgoing friend requests, acceptance/rejection, removal, duplicate prevention.
- Community membership, approved feeds, pending posts, leader moderation, comments.
- Private conversations between friends and groups with initial friend participants.
- Four-second message polling, persisted history, participant access checks.
- Profile editing, interests, goals, communities, relationship status.
- Admin statistics, user suspension/reactivation, community creation/editing/archiving, leader assignment, activity logs and daily reports.
- Dark violet glass interface, responsive navigation, subtle motion, desktop cursor, reduced-motion support.

## Technology Stack

| Layer | Technology |
|---|---|
| Frontend | HTML5, CSS3, vanilla JavaScript ES modules |
| Backend | Java 21+, JDK `HttpServer`, Java Collections |
| Persistence | JDBC with PostgreSQL JDBC Driver, PostgreSQL 14+ |
| JSON | Small JDK-only parser/writer in `util/Json.java` |
| Passwords | JDK PBKDF2-HMAC-SHA256, 120,000 iterations, random 16-byte salts |
| Tests | Java main-based tests; optional Python standard-library HTTP client and Node CDP browser client |

The PostgreSQL JDBC driver is the **only external application dependency**. It is a JDBC driver, not an ORM or framework. Python/Node are optional development test tools; neither runs the application backend. No npm install, build bundler, authentication framework, or frontend framework is required.

## OOP Concepts Demonstrated

The domain classes have private fields and validated construction. `Member` and `CommunityLeader` share behavior through inheritance, while `Admin` has a distinct dashboard. `PrivateConversation` and `GroupConversation` implement meaningful runtime polymorphism: the conversation list calls `getDisplayName(viewerId)` on a `Conversation`, obtaining either the peer's name or group name. `Post` implements `Approvable`; `DailyReport` implements `Reportable`.

See the [academic mapping](#academic-oop-mapping) and [architecture](docs/architecture.md).

## Folder Structure

```text
.
├── README.md / config.example.properties / .gitignore
├── database/                 schema.sql, seed.sql, schema.postgresql.sql, seed.postgresql.sql
├── docs/                     requirements, architecture, diagrams, API, testing
├── frontend/                 landing, authentication and member pages
│   ├── leader/               dashboard, approvals
│   ├── admin/                dashboard, users, communities, reports, activity
│   └── assets/               css/style.css, js modules
├── backend/
│   ├── build.ps1 / run.ps1 / test.ps1
│   ├── lib/                  place Connector/J here
│   ├── src/com/friendfinder/  Main, model, dao, service, controller,
│   │                         interfacepkg, report, util
│   └── test/                 DomainTest.java
├── tests/                    integration.py, browser.mjs
└── tools/                    optional initial asset/seed generator
```

## Requirements

- JDK 21 or newer (`java -version`, `javac -version`).
- MySQL 8.0.16+ (enforced CHECK constraints) or MySQL 8.4.
- MySQL Connector/J 9.4.0, or another compatible Connector/J version.
- Modern browser with JavaScript modules, Fetch, and `<dialog>` support.
- PowerShell for provided Windows scripts. Manual commands are also shown below.

## Database Setup (PostgreSQL)

Create the PostgreSQL database as an administrator:

```powershell
psql -U postgres -d postgres
```

In `psql`:

```sql
CREATE DATABASE it_friend_finder;
\c it_friend_finder
\i database/schema.postgresql.sql
\i database/seed.postgresql.sql
```

Use your PostgreSQL username/password in `config.local.properties`. `psql` paths are relative to the directory where you started the client. The seed can be run repeatedly because it uses `ON CONFLICT DO NOTHING`.

The seed is intended for a new academic database. It uses fixed IDs and `INSERT IGNORE` so re-running preserves existing demo fixtures, passwords, and moderation decisions. Do not use it to merge unrelated data into an existing schema. Posts/messages in seed data are illustrative records; daily reports count application activity logs, so fixture content does not manufacture report activity.

## Backend Setup

Download the PostgreSQL JDBC driver from Maven Central and place the JAR in `backend/lib/`, for example:

```text
https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.8/postgresql-42.7.8.jar
```

The driver is ignored by Git; source compilation requires only the JDK, but **database operations require this JAR at runtime**.

## Frontend Setup

No installation is required. The Java server serves `frontend/` and `/api/` from the same origin. Open the HTTP address below; opening HTML via `file://` will not work with the API.

## Configuration

```powershell
Copy-Item config.example.properties config.local.properties
```

Edit the ignored local file:

```properties
db.url=jdbc:mysql://127.0.0.1:3306/it_friend_finder?connectionTimeZone=UTC
db.user=friendfinder
db.password=change-me
server.port=8080
frontend.path=frontend
```

Environment variables override the file: `FF_DB_URL`, `FF_DB_USER`, `FF_DB_PASSWORD`, `FF_SERVER_PORT`, `FF_FRONTEND_PATH`. Run from the project root. Connections explicitly set their MySQL session time zone to UTC; daily reports are UTC calendar days, and the UI formats timestamps in the browser's local time.

## Running the Project

From the repository root:

```powershell
.\backend\build.ps1
.\backend\run.ps1
```

`run.ps1` also compiles before starting. Open **http://localhost:8080/**. Stop with Ctrl+C. If PowerShell blocks local scripts, use a process-scoped execution policy or the manual commands below, according to your machine's rules.

Manual Windows compilation/run:

```powershell
New-Item -ItemType Directory -Force backend/build | Out-Null
$sources = @(Get-ChildItem backend/src -Recurse -Filter *.java | ForEach-Object FullName)
javac --release 21 -encoding UTF-8 -d backend/build $sources
java -cp 'backend/build;backend/lib/*' com.friendfinder.Main
```

On Linux/macOS, use `:` instead of `;` in the Java classpath:

```sh
mkdir -p backend/build
find backend/src -name '*.java' > backend/sources.txt
javac --release 21 -encoding UTF-8 -d backend/build @backend/sources.txt
java -cp 'backend/build:backend/lib/*' com.friendfinder.Main
```

The server binds to `127.0.0.1` for the local academic demonstration. It serves frontend pages even if the database is unavailable; API errors then explain the configuration issue. `GET /api/health` verifies actual JDBC connectivity.

## Demo Accounts

**All demo passwords: `Demo123!`. These are obvious academic fixtures, not production credentials.**

| Username | Role | Notes |
|---|---|---|
| `admin` | Admin | Platform management and reports |
| `ai_leader` | Community leader | Artificial Intelligence, Machine Learning, Data Science |
| `tech_leader` | Community leader | The remaining seven seeded communities |
| `jason` | Member | AI/ML/data interests; friends with Nethmi |
| `nethmi` | Member | AI/software interests; sample pending AI post |
| `ravi` | Member | Security/network/cloud interests |
| `sasha` | Member | Web/mobile/software interests |
| `dilshan` | Member | ML/data interests |
| `maya` | Member | Web/cloud/software interests |

Email login also works, using `<username>@example.test`.

## Demonstration Workflow

1. Open the landing page, register a new account, and complete all five questionnaire steps. Give AI, ML, and Data Science high ratings to see relevant recommendations.
2. Open a suggested member's profile and send a friend request (for example, Jason).
3. In a separate browser profile/private window, log in as that member and accept under Friends. Both users now see the friendship.
4. Click Message on the friend card. Send a message; the other window refreshes within about four seconds.
5. From the new user's window, join Artificial Intelligence and submit a post. The feed hides it while it is pending.
6. Log in as `ai_leader` in another browser profile. Open Post approvals, preview, and approve the new user's post. `tech_leader` cannot approve AI posts; the author cannot approve their own post.
7. Refresh the community as the new user. The approved post is visible; add a comment.
8. Open Messages → New group. Name the group, select accepted friends, and send a group message. Participants can read/send; outsiders cannot.
9. Log in as `admin`. Observe dashboard/report activity from those actions, inspect activity, suspend/reactivate a member, and create/edit/archive a community or assign a leader.
10. Explain `MessageService.list()` runtime polymorphism, matching weights, and the separation between DAO, service, and HTTP controller.

Use separate browser profiles for concurrent accounts because the session cookie is shared between tabs of one profile. All protected operations re-check the account's current suspension and role.

## Testing

```powershell
.\backend\test.ps1
python tests/integration.py
```

The second command requires the configured server and a **disposable seeded database**. It creates persistent test users, posts, conversations, report events, and an archived community. It must not run against a database whose data you need to keep unchanged. See [testing](docs/testing.md) for browser checks and verification status.

## Troubleshooting

| Problem | Resolution |
|---|---|
| Database unavailable / SQL state `08001` | Put Connector/J in `backend/lib/`; run with the documented classpath; check host/port. |
| Access denied / SQL state `28000` | Verify database username/password and the MySQL account's host grant. |
| Unknown database/table | Execute schema, then seed, in the MySQL client. |
| Port already in use | Change `server.port`, then open that port in the browser. |
| `UnsupportedClassVersionError` | Use JDK 21+ for compile and runtime. |
| Script execution disabled | Use manual commands or an approved process-scoped PowerShell policy. |
| Redirected to login | Sessions expire after eight hours and are lost when the server restarts. |
| Rejected request cannot be resent | V1 intentionally prevents repeated requests after rejection. Removed friendships can be requested again. |
| Leader cannot leave community | Administrator must reassign the community first. |
| Private chat cannot send | Both accounts must be active and still friends. Existing history remains available to participants. |

## Academic OOP Mapping

| Concept | Concrete implementation |
|---|---|
| Classes and objects | User, Post, Message, Community and service-created model instances |
| Encapsulation / private attributes | Domain fields are private; immutable questionnaire collections and participant lists |
| Getters / setters / validation | User setters validate username/email/name; Post and Message reject blanks |
| Constructors / overloading | `Member(name,username,email)` delegates to `Member(id,name,username,email)`; Post draft/persisted constructors |
| Method overloading | `UserDAO.searchUsers(keyword)` and `searchUsers(keyword,interest)` |
| Inheritance | User → Member → CommunityLeader; User → Admin; Conversation subclasses |
| Overriding / polymorphism | Role-specific dashboards; private/group `getDisplayName()` used in actual conversation list |
| Interfaces | Post implements Approvable; DailyReport implements Reportable |
| Associations | User has QuestionnaireResponse; CommunityMembership associates Member and Community; Conversation has participants |
| Static members | Matching weights, questionnaire goal constants, password iteration constant, configuration utility |
| Arrays / String manipulation | Password salt byte array, JSON character array, username/email normalization, cookie/query parsing |
| Collections | ArrayList DAO results, Map ratings/JSON, Set goals/participants, ConcurrentHashMap sessions |
| JDBC | Resource-safe prepared statements, foreign keys, transactions including activity events |

## Known Limitations

This is a local academic application. Sessions are in memory and disappear on restart. The local HTTP cookie is HttpOnly/SameSite but is not Secure because the demo uses HTTP. Production deployment, rate limits, password recovery, pagination across large datasets, and HTTPS setup are outside this scope.

Recommendation candidates are limited to 200, searches/feeds to 100, activity to 200, and message loading to the latest 200 messages. Groups support 2–20 initial participants; later invitation/removal and history pagination are not included. A former leader retains their leader role after reassignment but can moderate only communities currently assigned to them. Leader-authored posts remain pending until another leader is assigned; self-moderation is deliberately prohibited.

Full JDBC integration and authenticated browser verification remain pending the driver dependency. The implementation is present, but those checks are not claimed as passed.
