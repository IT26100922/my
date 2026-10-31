"""Static serving and HTTP boundary tests; independent of a configured database."""
import json
from pathlib import Path
import urllib.error
import urllib.request

base = 'http://127.0.0.1:8080'
checks = 0

def check(path, expected, data=None, headers=None):
    global checks
    request = urllib.request.Request(base + path, data=data, headers=headers or {})
    try:
        response = urllib.request.urlopen(request, timeout=10)
    except urllib.error.HTTPError as error:
        response = error
    assert response.code == expected, (path, response.code, response.read().decode())
    checks += 1
    return response.read(), response.headers

for page in Path('frontend').rglob('*.html'):
    body, headers = check('/' + page.relative_to('frontend').as_posix(), 200)
    assert b'<html' in body
    assert "script-src 'self'" in headers.get('Content-Security-Policy', '')
for asset in Path('frontend/assets').rglob('*'):
    if asset.is_file():
        check('/' + asset.relative_to('frontend').as_posix(), 200)
check('/missing.html', 404)
check('/../README.md', 404)
check('/%2e%2e/README.md', 404)
check('/api/users/me', 401)
check('/api/register', 405)
check('/api/login', 400, b'{}')
check('/api/register', 400, b'{"broken":}', {'Content-Type': 'application/json'})
check('/api/register', 403, b'{}', {'Content-Type': 'application/json', 'Origin': 'http://other.example'})
check('/api/register', 413, b' ' * 65537, {'Content-Type': 'application/json'})
check('/api/register', 400, json.dumps({'fullName': 'Test', 'username': 'bad!'}).encode(), {'Content-Type': 'application/json'})
print(f'PASS: {checks} static/HTTP boundary checks')
