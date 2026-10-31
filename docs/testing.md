# Testing and verification

Verified in this workspace with Java compiling Java 21-compatible bytecode and Chrome headless. Database workflow tests should use PostgreSQL with the supplied PostgreSQL schema and seed; no in-memory persistence or mocked API responses are used.

## Executed checks

| Area | Evidence | Result |
|---|---|---|
| Backend compilation | `backend/build.ps1`, JDK `javac --release 21` | Pass |
| Domain behavior | `backend/test.ps1`, main-based Java assertions | 286 checks pass |
| Schema | Execute `database/schema.sql` with MySQL CLI | Pass, all 14 tables created |
| Seed | Execute `database/seed.sql` twice | Pass, repeatable fixture counts |
| DAO/report SQL | `tests/schema_audit.py`, real MySQL PREPARE | 61 literal statements prepared successfully |
| Database constraints | Same script: self/duplicate friendships, duplicate membership, invalid rating, unauthorized message sender, malformed private conversation | Six negative checks pass |
| HTTP/static boundary | `tests/http_smoke.py` against Java server | 33 checks pass |
| Frontend JavaScript | `node --check` for all four JS modules | Pass |
| API documentation | Compare controller routes with endpoint tables | All 43 method/route pairs match |
| Public browser rendering | `tests/browser.mjs` through Chrome DevTools | 18 checks pass |
| Landing responsiveness | Real Chrome at 1440, 768, 390px | No horizontal overflow; 10 topic cards |
| Auth forms | Browser labels/native required checks and mobile width | Pass |
| Reduced motion | Emulated reduced-motion media preference | Hero animation and custom cursor disabled |
| Cursor behavior | Chrome pointer movement over a button; mobile width | Desktop following/hover expansion works; mobile disables it |
| Public console | Browser Runtime/Log error collection | No JS exceptions or browser console errors |

Domain assertions cover identical/similar/different interest matching, symmetry, 250 randomized bounded scores, shared indifference, username/email/rating validation, immutable collections, pending post state, self/repeated moderation prevention, blank messages, conversation polymorphism, inherited leader behavior, salted password verification, JSON round trips/malformed input, UTC timestamp serialization, and all nine seeded credentials using Java's actual verifier.

The SQL audit prepares **literal** SQL in source files. It catches table/column/syntax mismatches but does not execute DAO methods, validate JDBC parameter binding, or prove transaction behavior. Nonliteral public-column query composition is reviewed in source. Constraint tests run inside transactions and reject invalid writes.

HTTP smoke checks all 17 HTML pages and six CSS/JS/image assets, unknown paths, traversal attempts, unauthenticated access, incorrect method/content type, malformed JSON, foreign Origin, oversized body, and invalid registration before persistence.

Screenshots generated during browser testing are under ignored `test-results/landing-1440.png`, `landing-768.png`, and `landing-390.png` and were visually inspected. Browser bootstrap initially failed because restricted Windows renderer/GPU processes could not start; a dedicated headless test profile with `--no-sandbox --in-process-gpu` allowed the public checks to run. This changes the test browser launch only, not the application's server/security settings.

## Pending verification boundary

Connector/J is not installed in the workspace. Direct download attempts were blocked by the execution environment's network permissions; no existing local driver was found. Therefore **authenticated JDBC/API workflows have not been executed** and are not represented as passing. Compilation, SQL preparation and public-page rendering are valuable but do not replace the full workflow.

Once a compatible MySQL Connector/J JAR is placed in `backend/lib/`, configure the database and run the server, then:

```powershell
.\backend\test.ps1
python tests/integration.py
```

The integration suite creates persistent fixtures and must run against a disposable schema initialized with schema.sql and seed.sql. It uses standard-library Python solely as an HTTP client. It fails immediately if `/api/health` cannot query MySQL.

## Implemented HTTP integration scenarios (not yet executed)

| Module | Scenarios |
|---|---|
| Register | Valid user, invalid email, mismatched passwords, duplicate username/email, no returned hash |
| Login | Email login, wrong password, suspended account, reactivated account |
| Questionnaire | All ten ratings persisted, empty goals rejected, onboarding required for matches |
| Matching | Self/friend/suspended excluded, bounded scores, descending order, community recommendations |
| Friends | Send, reverse/normal duplicates, self request, sender cannot accept, recipient accepts, bilateral list, reject, repeated rejection blocked, remove |
| Communities | Join, duplicate join, leave, nonmember cannot submit/comment |
| Posts | Pending submission, hidden feed, pending leader queue, member/wrong leader/admin denied, assigned leader approves, own leader post denied, reject stays hidden |
| Comments | Pending rejected, approved comment persists, community membership required |
| Private chat | Friends required, reuse pair, send/read persistence, outside read/write denied, admin content read denied, blank text rejected, removed friendship prevents sends |
| Group chat | Friend invite, creator added, membership persisted, participant sends/reads, nonfriend invite denied, polymorphic display name |
| Admin | Real statistics, event report, metadata excludes message text, member report denied, suspend blocks existing session/login, reactivate, create/assign/promote/archive community |
| Session | Logout invalidates session |

After the API suite passes, manually exercise the README demonstration workflow in separate browser profiles and check authenticated pages at desktop/mobile sizes, keyboard navigation, post/comment/message text escaping, success/error/empty/loading states, and the browser console. The current public browser suite does not claim those authenticated checks.

## Reproduction commands

Start the Java server first. Optional HTTP boundary test:

```powershell
python tests/http_smoke.py
```

MySQL audit (change executable/port/user to your disposable database):

```powershell
python tests/schema_audit.py --mysql 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe' --port 3307 --user root
```

For password-protected audit accounts, set `MYSQL_PWD` in the process environment rather than putting passwords in command-line arguments; remove it afterwards. The test instance used here is isolated on loopback port 3307 and does not touch the existing MySQL service on 3306.

Launch a dedicated Chrome/Edge debugging profile with `--headless=new --remote-debugging-port=9222 --user-data-dir=<new test folder>` using your approved browser launch method, then run `node tests/browser.mjs`. No npm dependency is required (Node 22+ provides WebSocket). Test screenshots/logs/profile files are ignored by Git.
