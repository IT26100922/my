# API endpoints

Base path: `/api`. GETs return JSON data directly. POSTs require `Content-Type: application/json` and a JSON object. IDs are positive integer JSON numbers in bodies or numeric query parameters. Authentication is an HttpOnly `ff_session` cookie set on register/login; the frontend uses same-origin fetch. No token or password hash is returned to the browser.

Permissions: **Public**, **Authenticated** (all active roles), **Member** (Member or CommunityLeader), **Leader** (CommunityLeader), **Admin**. Suspended accounts are blocked at authentication. UI redirects are convenience; Java independently checks permissions.

## Authentication and profile

| Method | Route | Permission | Request | Response/purpose |
|---|---|---|---|---|
| GET | /health | Public | — | `{status:"ok"}` after real database query; 503 if unavailable |
| POST | /register | Public | fullName, username, email, password, confirmPassword | 201 `{user:profile}` and session cookie; always creates MEMBER |
| POST | /login | Public | login (username/email), password | `{user:profile}` and session cookie |
| POST | /logout | Authenticated | `{}` | Revokes session and expires cookie |
| GET | /users/me | Authenticated | — | Own profile, dashboard URL, onboarded flag, ratings/goals/preferences/communities |
| POST | /users/me | Member | fullName, bio | Update own name/bio |
| GET | /users/profile | Member | `?id=4` | Active non-admin profile and `friend_request` (null or id/sender/receiver/status) |
| GET | /interests | Authenticated | — | Topic id/name array |
| POST | /questionnaire | Member | interests, experience, goals, collaboration, activity | Save all answers transactionally |
| GET | /matches | Member | — | Ranked profile array with integer `match`; 409 until onboarded |
| GET | /communities/recommended | Member | — | Ranked community array with interest-fit `match` |

Example questionnaire:

```json
{
  "interests": [{"id":1,"rating":5},{"id":2,"rating":4},{"id":3,"rating":2},{"id":4,"rating":4},{"id":5,"rating":3},{"id":6,"rating":2},{"id":7,"rating":5},{"id":8,"rating":2},{"id":9,"rating":3},{"id":10,"rating":3}],
  "experience":"Intermediate",
  "goals":["Friends","Project Partners"],
  "collaboration":"Both",
  "activity":"Daily"
}
```

Each interest must appear exactly once. Goals: Friends, Study Partners, Project Partners, Mentorship, Professional Networking. Experience: Beginner/Intermediate/Advanced. Collaboration: Mostly Discussion/Mostly Projects/Both. Activity: Casual/Few Times Per Week/Daily.

## Friendship

| Method | Route | Permission | Request | Purpose |
|---|---|---|---|---|
| GET | /friends | Member | — | Accepted active friends with id/name/username/bio/request_id |
| GET | /friends/requests | Member | — | Incoming and outgoing PENDING requests, sender_name/receiver_name |
| POST | /friends/request | Member | userId | Send to active non-admin, excluding self/existing pair |
| POST | /friends/accept | Member recipient | requestId | Accept pending request and log friendship |
| POST | /friends/reject | Member recipient | requestId | Reject pending request; repeated requests remain blocked |
| POST | /friends/remove | Member participant | userId | Remove accepted friendship |

## Communities and forum

| Method | Route | Permission | Request | Purpose |
|---|---|---|---|---|
| GET | /communities | Authenticated | — | Active community rows with leader_name, derived member_count, own joined flag |
| GET | /communities/members | Authenticated | `?communityId=1` | Active members' id/name/username/role; active community required |
| POST | /communities/join | Member | communityId | Join active community, reject duplicate |
| POST | /communities/leave | Member | communityId | Leave; assigned leader must remain until reassigned |
| GET | /posts | Authenticated | Optional `?communityId=1` | APPROVED posts in active communities; latest 100 |
| POST | /posts | Member in community | communityId, title, body | Create PENDING post |
| GET | /posts/pending | Leader | — | Pending posts in own assigned active communities |
| POST | /posts/approve | Assigned leader | postId | Approve pending post, excluding own post |
| POST | /posts/reject | Assigned leader | postId | Reject pending post, excluding own post |
| GET | /comments | Authenticated | `?postId=1` | Comments for an approved post in active community; otherwise empty array |
| POST | /comments | Member in community | postId, body | Comment on approved post only |

Post titles: 1–160 characters; bodies: 1–10,000. Comments: 1–2,000.

## Conversations and messages

| Method | Route | Permission | Request | Purpose |
|---|---|---|---|---|
| GET | /conversations | Member | — | Only own conversations; `display_name` comes from polymorphic Java method |
| POST | /conversations/private | Member, friends | userId | Create/reuse private conversation with friend; returns id |
| POST | /conversations/group | Member | name, participants | Create named group with 1–19 active friend IDs; creator added |
| GET | /messages | Member participant | `?conversationId=1` | `{messages:[...],participants:[...]}`; latest 200 chronological messages |
| POST | /messages | Member participant | conversationId, body | Persist message and metadata-only activity event |

Messages: 1–4,000 characters. Group names: 1–100. Private sends require current friendship. Any suspended participant pauses sends. Administrators are not members and cannot access message contents via these routes.

## Administration

| Method | Route | Permission | Request | Purpose |
|---|---|---|---|---|
| GET | /admin/dashboard | Admin | — | total_users, active_users, total_communities, report |
| GET | /admin/report | Admin | Optional `?date=2026-10-05` | UTC daily event metrics and most-active user/community |
| GET | /admin/users | Admin | Optional `?search=jason&interest=Artificial%20Intelligence` | Up to 100 user summaries; overloaded search with optional highly rated interest |
| POST | /admin/users/suspend | Admin | userId | Suspend non-admin; current sessions blocked immediately |
| POST | /admin/users/reactivate | Admin | userId | Reactivate non-admin |
| GET | /admin/communities | Admin | — | Communities including archived rows |
| POST | /admin/communities/create | Admin | name, description, interestId (number/null) | Create active community; returns id |
| POST | /admin/communities/edit | Admin | id, name, description, active (boolean), interestId (number/null) | Edit/archive/reactivate community |
| POST | /admin/communities/leader | Admin | communityId, leaderId | Assign active member, promote role and ensure membership |
| GET | /admin/activity | Admin | — | Latest 200 event/actor/community/time rows; no message body |

Report keys: date, new_users, deactivated_users, new_friendships, community_joins, community_leaves, posts_submitted, posts_approved, posts_rejected, comments, messages_sent, most_active_user, most_active_community. Most-active objects are null when no events exist on the date. Dates must use ISO `YYYY-MM-DD`.

## Response conventions

Successful changes normally return `{ "message": "Saved successfully" }`, with `id` added for created resources (or a reused private conversation). Registration returns 201; other successful mutations return 200. Java SQL TIMESTAMP values serialize as ISO UTC strings. MySQL boolean fields are JSON booleans with Connector/J's default TINYINT(1) behavior.

Errors return `{ "error": "Readable description" }`:

| Status | Meaning |
|---|---|
| 400 | Invalid input/JSON, unsupported choice, wrong content type |
| 401 | Missing/expired/revoked session, invalid login |
| 403 | Suspended account, role/ownership/membership restriction, cross-origin POST |
| 404 | Missing resource or unknown endpoint |
| 405 | Unsupported method (GET/POST only; auth routes require POST) |
| 409 | Duplicate/conflict, already answered/moderated, missing onboarding, archived community |
| 413 | Request exceeds 64 KiB |
| 500 | Unexpected application failure |
| 503 | Database/driver/configuration unavailable |

SQL constraint errors are translated to a generic conflict; SQL details/passwords/credentials are never exposed to the browser. Resource names, database syntax and dates are fixed/prevalidated in Java; all SQL data values bind through PreparedStatement.
