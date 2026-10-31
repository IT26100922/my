"""Development helper only. Writes initial page shells and deterministic demo SQL."""
from pathlib import Path
import base64
import hashlib

ROOT = Path(__file__).resolve().parents[1]
pages = {
    'questionnaire': 'questionnaire', 'dashboard': 'dashboard', 'profile': 'profile',
    'friends': 'friends', 'communities': 'communities', 'community': 'community',
    'messages': 'messages', 'leader/dashboard': 'leader-dashboard', 'leader/approvals': 'approvals',
    'admin/dashboard': 'admin-dashboard', 'admin/users': 'admin-users',
    'admin/communities': 'admin-communities', 'admin/reports': 'admin-reports', 'admin/activity': 'admin-activity'
}
for filename, page in pages.items():
    title = page.replace('-', ' ').title()
    path = ROOT / 'frontend' / (filename + '.html')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f'''<!doctype html>
<html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>{title} — IT Friend Finder</title><link rel="icon" href="/assets/images/favicon.svg"><link rel="stylesheet" href="/assets/css/style.css"><script type="module" src="/assets/js/app.js"></script></head>
<body data-page="{page}"><div class="ambient"></div><div class="app-layout"><aside class="sidebar" id="sidebar" aria-label="Sidebar"></aside><main class="app-main"><header class="topbar" id="topbar"></header><div class="page"><div id="notice" class="notice" role="alert"></div><div id="content"><div class="loading" role="status"><span class="spinner"></span>Getting your workspace ready…</div></div></div></main></div><div id="toast" class="toast notice success" role="status"></div></body></html>''', encoding='utf-8')

def sql(value):
    return "'" + str(value).replace("'", "''") + "'"

def password_hash(username):
    salt = hashlib.sha256(('demo-only-' + username).encode()).digest()[:16]
    digest = hashlib.pbkdf2_hmac('sha256', b'Demo123!', salt, 120000)
    return f'120000:{base64.b64encode(salt).decode()}:{base64.b64encode(digest).decode()}'

topics = ['Artificial Intelligence', 'Machine Learning', 'Cyber Security', 'Software Engineering', 'Web Development', 'Mobile Development', 'Data Science', 'Networking', 'Cloud Computing', 'DevOps']
descriptions = [
    'Explore intelligent systems, responsible AI, and practical projects together.',
    'Learn algorithms, train models, and share your latest experiments.',
    'Learn ethical security practices and keep systems resilient.',
    'Discuss clean code, Java OOP, architecture, and collaborative engineering.',
    'Build accessible websites and exchange frontend and backend knowledge.',
    'Create useful mobile experiences and learn app development together.',
    'Explore datasets, visualization, statistics, and meaningful discoveries.',
    'Understand connected systems, routing, and network fundamentals.',
    'Learn cloud infrastructure, deployment concepts, and scalable systems.',
    'Discuss automation, testing, delivery, and collaborative operations.'
]
accounts = [
    (1, 'Platform Admin', 'admin', 'ADMIN'),
    (2, 'Amaya Silva', 'ai_leader', 'COMMUNITY_LEADER'),
    (3, 'Kavindu Fernando', 'tech_leader', 'COMMUNITY_LEADER'),
    (4, 'Jason Perera', 'jason', 'MEMBER'),
    (5, 'Nethmi Jayasinghe', 'nethmi', 'MEMBER'),
    (6, 'Ravi Wijesinghe', 'ravi', 'MEMBER'),
    (7, 'Sasha Fernando', 'sasha', 'MEMBER'),
    (8, 'Dilshan Silva', 'dilshan', 'MEMBER'),
    (9, 'Maya Senanayake', 'maya', 'MEMBER')
]
lines = ['-- DEMO ONLY. Every account uses Demo123!; deterministic salts are for repeatable fixtures.',
         '-- INSERT IGNORE preserves existing demonstration records when this seed is re-run.',
         'USE it_friend_finder;', 'START TRANSACTION;']
for uid, name, username, role in accounts:
    values = [str(uid), sql(name), sql(username), sql(username + '@example.test'), sql(password_hash(username)), sql(role), sql('Curious about technology. Looking to learn and build with others.')]
    lines.append('INSERT IGNORE INTO users(id,full_name,username,email,password_hash,role,bio) VALUES(' + ','.join(values) + ');')
for iid, (topic, description) in enumerate(zip(topics, descriptions), 1):
    lines.append(f'INSERT IGNORE INTO interests(id,name) VALUES({iid},{sql(topic)});')
    leader = 2 if iid in (1, 2, 7) else 3
    lines.append(f'INSERT IGNORE INTO communities(id,interest_id,name,description,leader_id) VALUES({iid},{iid},{sql(topic)},{sql(description)},{leader});')
    lines.append(f'INSERT IGNORE INTO community_members(user_id,community_id) VALUES({leader},{iid});')
ratings = {2:[5,5,2,4,3,2,5,2,4,3], 3:[2,3,5,5,5,4,3,4,5,5], 4:[5,5,2,4,3,2,5,2,4,3], 5:[5,4,2,5,4,2,5,2,3,3], 6:[1,2,5,4,3,2,2,5,5,5], 7:[3,3,2,5,5,5,3,2,3,3], 8:[4,5,3,4,2,2,5,3,4,4], 9:[2,2,4,5,5,4,2,4,5,5]}
for uid, values in ratings.items():
    level = 'Advanced' if uid in (2,3) else 'Beginner' if uid in (7,9) else 'Intermediate'
    activity = 'Daily' if uid < 6 else 'Few Times Per Week'
    lines.append(f"INSERT IGNORE INTO questionnaire_responses(user_id,experience,collaboration,activity) VALUES({uid},{sql(level)},'Both',{sql(activity)});")
    for iid, rating in enumerate(values, 1):
        lines.append(f'INSERT IGNORE INTO user_interests(user_id,interest_id,rating) VALUES({uid},{iid},{rating});')
    for goal in ('Friends', 'Project Partners', 'Study Partners'):
        lines.append(f'INSERT IGNORE INTO user_goals(user_id,goal) VALUES({uid},{sql(goal)});')
    for iid in (1,4,5):
        lines.append(f'INSERT IGNORE INTO community_members(user_id,community_id) VALUES({uid},{iid});')
lines += [
    "INSERT IGNORE INTO posts(id,community_id,author_id,title,body,status,moderated_by,moderated_at) VALUES(1,1,4,'Let us build a beginner-friendly AI study group','I am exploring search algorithms and introductory machine learning. What would you like to learn together?','APPROVED',2,CURRENT_TIMESTAMP);",
    "INSERT IGNORE INTO posts(id,community_id,author_id,title,body,status,moderated_by,moderated_at) VALUES(2,4,5,'Our first Java OOP project','Let us exchange ideas about encapsulation, inheritance, and readable Java classes.','APPROVED',3,CURRENT_TIMESTAMP);",
    "INSERT IGNORE INTO posts(id,community_id,author_id,title,body,status) VALUES(3,1,5,'A weekend project idea','Could we build a simple classifier using a public dataset? I would love some collaborators.','PENDING');",
    "INSERT IGNORE INTO comments(id,post_id,author_id,body) VALUES(1,1,5,'Count me in. I would like to start with the fundamentals.');",
    "INSERT IGNORE INTO friend_requests(id,sender_id,receiver_id,status,responded_at) VALUES(1,4,5,'ACCEPTED',CURRENT_TIMESTAMP);",
    "INSERT IGNORE INTO conversations(id,type,name,creator_id,private_low,private_high) VALUES(1,'PRIVATE','',4,4,5);",
    "INSERT IGNORE INTO conversation_members(conversation_id,user_id) VALUES(1,4),(1,5);",
    "INSERT IGNORE INTO messages(id,conversation_id,sender_id,body) VALUES(1,1,4,'Hi Nethmi! Shall we brainstorm a Java project?'),(2,1,5,'Absolutely. Let us start with a simple plan.');",
    'COMMIT;'
]
(ROOT / 'database' / 'seed.sql').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(f'Generated {len(pages)} page shells and demo SQL.')
