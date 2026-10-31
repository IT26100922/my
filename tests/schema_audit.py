"""Validate literal DAO/report SQL with real MySQL PREPARE, plus schema invariants.

No Connector/J required. Does not substitute for JDBC/API integration tests.
Usage: python tests/schema_audit.py --mysql /path/mysql.exe --port 3307 --user root
Use MYSQL_PWD in the environment if this isolated test account needs a password.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--mysql', default='mysql')
parser.add_argument('--port', default='3306')
parser.add_argument('--user', default='root')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
statements = set()
for path in (root / 'backend/src').rglob('*.java'):
    text = path.read_text(encoding='utf-8')
    for value in re.findall(r'"((?:[^"\\]|\\.)*)"', text):
        if re.match(r'^(SELECT|INSERT|UPDATE|DELETE)\s', value) and len(value.split()) > 3:
            value = json.loads('"' + value + '"')
            statements.add(value)

sql = ['USE it_friend_finder;']
for index, statement in enumerate(sorted(statements)):
    # PREPARE parses and validates table/column names without modifying rows.
    encoded = statement.encode('utf-8').hex()
    sql += [f'SET @statement = CONVERT(0x{encoded} USING utf8mb4);', 'PREPARE audit FROM @statement;', 'DEALLOCATE PREPARE audit;']
command = [args.mysql, '--no-defaults', '--host=127.0.0.1', '--port=' + args.port, '--user=' + args.user, '--default-character-set=utf8mb4', '--batch', '--skip-column-names']
result = subprocess.run(command, input='\n'.join(sql), text=True, capture_output=True)
assert result.returncode == 0, result.stderr
print(f'PASS: {len(statements)} literal SQL statements prepared by MySQL')

def rejects(statement, label):
    result = subprocess.run(command, input='USE it_friend_finder; START TRANSACTION; ' + statement + '; ROLLBACK;', text=True, capture_output=True)
    assert result.returncode != 0, label
    print('PASS:', label)

rejects("INSERT INTO friend_requests(sender_id,receiver_id) VALUES(4,4)", 'Database rejects self friendship')
rejects("INSERT INTO friend_requests(sender_id,receiver_id) VALUES(5,4)", 'Database rejects reverse duplicate friendship')
rejects("INSERT INTO community_members(user_id,community_id) VALUES(2,1)", 'Database rejects duplicate membership')
rejects("INSERT INTO user_interests(user_id,interest_id,rating) VALUES(1,1,6)", 'Database rejects invalid interest rating')
rejects("INSERT INTO messages(conversation_id,sender_id,body) VALUES(1,6,'Unauthorized')", 'Database rejects sender outside conversation')
rejects("INSERT INTO conversations(type,name,creator_id,private_low,private_high) VALUES('PRIVATE','',4,NULL,NULL)", 'Database rejects malformed private conversation')
