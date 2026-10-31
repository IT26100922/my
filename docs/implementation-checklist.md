# Implementation checklist

Repository inspection: empty workspace; Java 25 and MySQL 8 binaries available.

- [x] Foundation: centralized configuration, HTTP server, build/run scripts, design tokens
- [x] Database: normalized schema and repeatable demonstration seed (MySQL 8.4 verified)
- [x] Domain: encapsulation, validation, role and conversation inheritance, interfaces
- [x] Persistence implementation: prepared JDBC statements and transaction support
- [x] Services/API implementation: authentication and server-side access rules
- [x] Onboarding and weighted matching implementation
- [x] Friend requests and friendship lifecycle implementation
- [x] Communities, moderated posts, comments implementation
- [x] Private and group conversations with polling implementation
- [x] Administration and daily activity reports implementation
- [x] Responsive frontend, accessible forms, cursor and reduced motion
- [x] Compilation, domain tests, MySQL SQL/constraint audit, public browser and HTTP tests
- [x] README, diagrams, API reference, test evidence
- [ ] Connector/J available at runtime (external downloads blocked; local copy requested)
- [ ] Real MySQL HTTP integration suite executed and any failures fixed
- [ ] Authenticated browser workflow/console checks executed against real APIs

Checked implementation items indicate code exists; they do not claim unexecuted workflows have passed. The final two verification items depend on the missing JDBC driver.
