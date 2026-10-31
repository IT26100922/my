"""Real HTTP/MySQL workflow checks. Run only against a disposable seeded database.

Uses Python's standard library as a test client, never as the application backend.
"""
import argparse
import http.cookiejar
import json
import time
import urllib.error
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('--base', default='http://127.0.0.1:8080')
args = parser.parse_args()
checks = []

class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def request(self, path, data=None, expected=200, label=None):
        request = urllib.request.Request(args.base + '/api' + path, data=None if data is None else json.dumps(data).encode(), headers={} if data is None else {'Content-Type': 'application/json'})
        try:
            response = self.opener.open(request, timeout=15)
        except urllib.error.HTTPError as error:
            response = error
        body = json.loads(response.read())
        assert response.code == expected, f'{label or path}: expected {expected}, got {response.code}: {body}'
        checks.append(label or path)
        return body

    def login(self, username):
        return self.request('/login', {'login': username, 'password': 'Demo123!'})['user']

def expect(condition, label):
    assert condition, label
    checks.append(label)

visitor = Client()
visitor.request('/health', label='MySQL-backed health check')
visitor.request('/friends', expected=401, label='Unauthenticated access rejected')
suffix = str(time.time_ns())[-12:]
credentials = {'fullName': 'Integration Member', 'username': 'test_' + suffix, 'email': 'test_' + suffix + '@example.test', 'password': 'Demo123!', 'confirmPassword': 'Demo123!'}
visitor.request('/register', {**credentials, 'email': 'bad'}, 400, 'Invalid registration rejected')
visitor.request('/register', {**credentials, 'confirmPassword': 'Different!'}, 400, 'Password confirmation enforced')
a = Client()
registered = a.request('/register', credentials, 201, 'Valid registration')
aid = registered['user']['id']
expect('password_hash' not in json.dumps(registered), 'Password hash never returned')
visitor.request('/register', {**credentials, 'email': 'other_' + suffix + '@example.test'}, 409, 'Duplicate username rejected')
visitor.request('/register', {**credentials, 'username': 'other_' + suffix}, 409, 'Duplicate email rejected')
visitor.request('/login', {'login': credentials['username'], 'password': 'bad'}, 401, 'Wrong password rejected')
a.request('/login', {'login': credentials['email'], 'password': 'Demo123!'}, label='Login by email')
a.request('/matches', expected=409, label='Questionnaire required for recommendations')
interests = a.request('/interests')
answers = {'interests': [{'id': i['id'], 'rating': 5 if i['id'] in (1, 2, 7) else 3} for i in interests], 'experience': 'Intermediate', 'goals': ['Friends', 'Project Partners'], 'collaboration': 'Both', 'activity': 'Daily'}
a.request('/questionnaire', {**answers, 'goals': []}, 400, 'Empty goals rejected')
a.request('/questionnaire', answers, label='Questionnaire saved')
matches = a.request('/matches')
expect(all(m['id'] != aid for m in matches), 'Self excluded from matches')
expect(all(0 <= m['match'] <= 100 for m in matches), 'All scores bounded')
expect([m['match'] for m in matches] == sorted([m['match'] for m in matches], reverse=True), 'Matches sorted by score')
a.request('/communities/recommended', label='Community recommendations')
b, leader, wrong_leader, admin, outsider = [Client() for _ in range(5)]
bob = b.login('jason'); bid = bob['id']
leader.login('ai_leader'); wrong_leader.login('tech_leader'); admin.login('admin'); outsider.login('ravi')
a.request('/friends/request', {'userId': aid}, 400, 'Self request rejected')
rid = a.request('/friends/request', {'userId': bid}, label='Send friend request')['id']
b.request('/friends/request', {'userId': aid}, 409, 'Reverse duplicate request rejected')
a.request('/friends/request', {'userId': bid}, 409, 'Duplicate request rejected')
a.request('/friends/accept', {'requestId': rid}, 403, 'Sender cannot accept own request')
b.request('/friends/accept', {'requestId': rid}, label='Recipient accepts request')
expect(any(f['id'] == bid for f in a.request('/friends')), 'Friend visible to sender')
expect(any(f['id'] == aid for f in b.request('/friends')), 'Friend visible to receiver')
expect(all(m['id'] != bid for m in a.request('/matches')), 'Existing friend excluded from recommendations')
cid = a.request('/conversations/private', {'userId': bid}, label='Create private conversation')['id']
expect(a.request('/conversations/private', {'userId': bid})['id'] == cid, 'Reuse private conversation')
mid = a.request('/messages', {'conversationId': cid, 'body': 'Hello <script>safe text</script>'}, label='Persist private message')['id']
expect(any(m['id'] == mid for m in b.request('/messages?conversationId=' + str(cid))['messages']), 'Recipient reads persisted message')
outsider.request('/messages?conversationId=' + str(cid), expected=403, label='Outsider cannot read messages')
outsider.request('/messages', {'conversationId': cid, 'body': 'Intrusion'}, 403, 'Outsider cannot send message')
admin.request('/messages?conversationId=' + str(cid), expected=403, label='Admin cannot browse private contents')
a.request('/messages', {'conversationId': cid, 'body': ' '}, 400, 'Blank message rejected')
gid = a.request('/conversations/group', {'name': 'Integration project team', 'participants': [bid]}, label='Create group with friend')['id']
group_mid = b.request('/messages', {'conversationId': gid, 'body': 'Group hello'}, label='Group participant sends')['id']
group = a.request('/messages?conversationId=' + str(gid))
expect(len(group['participants']) == 2 and any(m['id'] == group_mid for m in group['messages']), 'Group members and message persist')
expect(any(c['display_name'] == 'Integration project team' for c in a.request('/conversations')), 'Polymorphic group display name')
a.request('/conversations/group', {'name': 'Invalid group', 'participants': [6]}, 403, 'Group cannot invite nonfriend')
a.request('/communities/join', {'communityId': 1}, label='Join AI community')
a.request('/communities/join', {'communityId': 1}, 409, 'Duplicate join prevented')
postid = a.request('/posts', {'communityId': 1, 'title': 'Integration idea ' + suffix, 'body': 'Let us build a Java project.'}, label='Submit pending post')['id']
expect(all(p['id'] != postid for p in a.request('/posts?communityId=1')), 'Pending post hidden from public feed')
expect(any(p['id'] == postid and p['status'] == 'PENDING' for p in leader.request('/posts/pending')), 'Pending state visible to assigned leader')
a.request('/posts/approve', {'postId': postid}, 403, 'Member cannot approve')
wrong_leader.request('/posts/approve', {'postId': postid}, 403, 'Wrong community leader cannot approve')
admin.request('/posts/approve', {'postId': postid}, 403, 'Admin cannot routinely moderate posts')
a.request('/comments', {'postId': postid, 'body': 'Too early'}, 409, 'Cannot comment on pending post')
leader.request('/posts/approve', {'postId': postid}, label='Assigned leader approves')
expect(any(p['id'] == postid for p in a.request('/posts?communityId=1')), 'Approved post publicly visible')
a.request('/comments', {'postId': postid, 'body': 'Great, let us begin.'}, label='Comment on approved post')
expect(len(a.request('/comments?postId=' + str(postid))) == 1, 'Comment persists')
leader_post = leader.request('/posts', {'communityId': 1, 'title': 'Leader self moderation check', 'body': 'A leader cannot approve this themselves.'})['id']
leader.request('/posts/approve', {'postId': leader_post}, 403, 'Leader cannot approve own post')
rejected_post = a.request('/posts', {'communityId': 1, 'title': 'Rejected fixture', 'body': 'A fixture for rejection.'})['id']
leader.request('/posts/reject', {'postId': rejected_post}, label='Reject pending post')
expect(all(p['id'] != rejected_post for p in a.request('/posts?communityId=1')), 'Rejected post remains hidden')
a.request('/communities/leave', {'communityId': 1}, label='Leave community')
a.request('/posts', {'communityId': 1, 'title': 'Unauthorized', 'body': 'Not a member'}, 403, 'Nonmember cannot submit post')
a.request('/comments', {'postId': postid, 'body': 'Not a member'}, 403, 'Nonmember cannot comment')
a.request('/friends/remove', {'userId': bid}, label='Remove friend')
a.request('/messages', {'conversationId': cid, 'body': 'Friendship removed'}, 403, 'Removed friendship blocks new private messages')
rid2 = a.request('/friends/request', {'userId': 6})['id']
outsider.request('/friends/reject', {'requestId': rid2}, label='Reject friend request')
a.request('/friends/request', {'userId': 6}, 409, 'Repeated rejected request prevented')
summary = admin.request('/admin/dashboard', label='Admin dashboard loads real counts')
expect(summary['total_users'] >= 10, 'Total users includes registered fixture')
report = admin.request('/admin/report', label='Daily report loads')
admin.request('/admin/report?date=2026-02-30', expected=400, label='Invalid report date rejected')
expect(report['messages_sent'] >= 2 and report['posts_approved'] >= 1 and report['new_friendships'] >= 1, 'Report derives workflow events')
expect('Hello <script>' not in json.dumps(admin.request('/admin/activity')), 'Activity contains no private message contents')
a.request('/admin/report', expected=403, label='Member cannot read admin report')
admin.request('/admin/users/suspend', {'userId': aid}, label='Suspend account')
a.request('/friends', expected=403, label='Existing session blocked when suspended')
visitor.request('/login', {'login': credentials['username'], 'password': 'Demo123!'}, 403, 'Suspended account cannot log in')
expect(all(m['id'] != aid for m in b.request('/matches')), 'Suspended member excluded from recommendations')
admin.request('/admin/users/reactivate', {'userId': aid}, label='Reactivate account')
a.request('/login', {'login': credentials['username'], 'password': 'Demo123!'}, label='Reactivated login works')
new_community = admin.request('/admin/communities/create', {'name': 'Test community ' + suffix, 'description': 'Academic integration fixture', 'interestId': 4})['id']
admin.request('/admin/communities/leader', {'communityId': new_community, 'leaderId': aid}, label='Assign and promote community leader')
expect(a.request('/users/me')['role'] == 'COMMUNITY_LEADER', 'Role updated server-side for existing session')
admin.request('/admin/communities/edit', {'id': new_community, 'name': 'Test community ' + suffix, 'description': 'Archived fixture', 'active': False, 'interestId': 4}, label='Archive community')
expect(all(c['id'] != new_community for c in a.request('/communities')), 'Archived community hidden')
expect(any(c['id'] == new_community for c in admin.request('/admin/communities')), 'Admin sees archived community')
a.request('/logout', {}, label='Logout')
a.request('/users/me', expected=401, label='Session revoked on logout')
print(f'PASS: {len(checks)} HTTP/MySQL checks')
for label in checks:
    print('  PASS', label)
