"""Local-only session/CSRF verification. Never print credentials, tokens or session IDs."""
import argparse
import http.cookiejar
from http.cookies import SimpleCookie
import json
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--running', action='store_true', help='Verify the existing workorder-m4c-20261010 local project')
    parser.add_argument('--evidence', action='store_true')
    parser.add_argument('--expiry', action='store_true', help='Require the verification stack to use 1m inactivity timeout')
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    environment = dict(os.environ)
    for key in list(environment):
        if key.startswith('LOCAL_') or key in ('COMPOSE_FILE','COMPOSE_PROFILES','COMPOSE_PROJECT_NAME'):
            del environment[key]
    environment.update(LOCAL_BROWSER_ENABLED='true', LOCAL_BROWSER_SESSION_TIMEOUT='1m' if args.expiry else '15m')
    project = 'workorder-m4c-20261010' if args.running else 'workorder-session-' + secrets.token_hex(6)
    # Live local verification reuses the explicitly supplied ignored config, without editing it.
    if args.running:
        local = {}
        for line in (repo / '.env.compose.local').read_text().splitlines():
            if '=' in line and not line.startswith('#'):
                key, value = line.split('=', 1)
                local[key] = value.strip().strip("\"'")
        environment.update(local)
    else:
        environment.update(LOCAL_DB_USERNAME='session_fixture', LOCAL_DB_PASSWORD=secrets.token_hex(24),
                           LOCAL_APP_USERNAME='session_fixture', LOCAL_APP_PASSWORD=secrets.token_hex(24), LOCAL_DEMO_SEED='false')
    secret_values = [environment['LOCAL_DB_PASSWORD'], environment['LOCAL_APP_PASSWORD']]
    evidence = {'checks': [], 'fictionalOnly': True, 'apiLoopbackOnly': True}
    def check(condition, name):
        if not condition:
            raise AssertionError(name)
        evidence['checks'].append(name)
        print('PASS: ' + name, flush=True)
    def run(command, check_result=True):
        result = subprocess.run(command, cwd=repo, env=environment, capture_output=True, text=True, timeout=240)
        if check_result and result.returncode:
            output = result.stdout + result.stderr
            for value in secret_values: output = output.replace(value, '[redacted]')
            raise RuntimeError('Local Compose command failed: ' + output)
        return result
    with tempfile.TemporaryDirectory(prefix='workorder-session-') as temporary:
        empty = Path(temporary) / 'empty.env'; empty.write_text('')
        compose = ['docker','compose','--env-file',str(empty),'-p',project,'-f','compose.yaml','-f','compose.local-browser.yaml']
        created = False
        if not args.running:
            check(run(['docker','volume','inspect',project+'_postgres-data'], False).returncode != 0, 'refused pre-existing verification volume')
        try:
            if not args.running:
                created = True
                run(compose + ['up','-d','--no-build','--wait','--wait-timeout','180'])
            api = run(compose + ['ps','-q','api']).stdout.strip()
            db = run(compose + ['ps','-q','db']).stdout.strip()
            api_ports = json.loads(run(['docker','inspect','--format','{{json .HostConfig.PortBindings}}',api]).stdout)
            check(api_ports == {'8080/tcp':[{'HostIp':'127.0.0.1','HostPort':'8080'}]}, 'API bound only to 127.0.0.1:8080')
            check(not json.loads(run(['docker','inspect','--format','{{json .HostConfig.PortBindings}}',db]).stdout), 'PostgreSQL remains unpublished')
            jar = http.cookiejar.CookieJar()
            client = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect(), urllib.request.HTTPCookieProcessor(jar))
            base = 'http://127.0.0.1:8080'
            origin = 'http://127.0.0.1:4200'
            def cookie(name):
                return next((c.value for c in jar if c.name == name), None)
            def request(path, method='GET', body=None, xsrf=True, origin_value=origin, override=None):
                headers = {'Accept':'application/json'}
                data = None
                if method not in ('GET','HEAD'):
                    headers['Origin'] = origin_value
                    if xsrf and cookie('M4C-XSRF'): headers['X-M4C-XSRF'] = cookie('M4C-XSRF')
                if override: headers.update(override)
                if body is not None:
                    if path.endswith('/login'):
                        data = urllib.parse.urlencode(body).encode(); headers['Content-Type']='application/x-www-form-urlencoded'
                    else:
                        data = json.dumps(body).encode(); headers['Content-Type']='application/json'
                req = urllib.request.Request(base+path, data=data, headers=headers, method=method)
                try:
                    response = client.open(req, timeout=15)
                except urllib.error.HTTPError as error:
                    response = error
                raw = response.read()
                try: payload = json.loads(raw) if raw else None
                except ValueError: payload = None
                return response.code, response.headers, payload
            auth = '/api/local-auth'
            check(request('/api/work-orders', 'POST', {})[0] == 401, 'anonymous write rejected with 401')
            request(auth+'/csrf')
            credentials = {'username':environment['LOCAL_APP_USERNAME'],'password':environment['LOCAL_APP_PASSWORD']}
            check(request(auth+'/login','POST',credentials,xsrf=False)[0] == 403, 'login missing CSRF rejected with 403')
            check(request(auth+'/login','POST',credentials,override={'X-M4C-XSRF':'invalid'})[0] == 403, 'login invalid CSRF rejected with 403')
            wrong = dict(credentials); wrong['password']='intentionally-wrong-fixture'
            check(request(auth+'/login','POST',wrong)[0] == 401, 'incorrect credentials rejected with 401')
            prior_token = cookie('M4C-XSRF')
            status, headers, _ = request(auth+'/login','POST',credentials)
            check(status == 204 and cookie('M4CSESSION') is not None, 'correct login creates session without Basic challenge')
            check(headers.get('WWW-Authenticate') is None, 'no browser Basic challenge')
            parsed = SimpleCookie()
            for value in headers.get_all('Set-Cookie',[]): parsed.load(value)
            session_cookie = parsed['M4CSESSION']
            check(session_cookie['httponly'] and session_cookie['samesite']=='Strict' and session_cookie['path']=='/api'
                  and not session_cookie['secure'] and not session_cookie['max-age'], 'HTTP exception: HttpOnly Strict session cookie without persistence')
            request(auth+'/csrf')
            check(cookie('M4C-XSRF') != prior_token, 'CSRF refreshed after login rotation')
            old_session = cookie('M4CSESSION')
            check(request(auth+'/login','POST',credentials)[0] == 204 and cookie('M4CSESSION') != old_session, 'session ID changes on reauthentication')
            active_session = cookie('M4CSESSION')
            request(auth+'/csrf')
            check(request(auth+'/session')[2] == {'authenticated':True}, 'session status restored with cookie only')
            for method, path in [('POST','/api/work-orders'),('PUT','/api/work-orders/1'),('PATCH','/api/work-orders/1/status'),('DELETE','/api/work-orders/1')]:
                check(request(path,method,{},xsrf=False)[0] == 403, method+' missing CSRF rejected')
            check(request('/api/work-orders','POST',{},override={'X-M4C-XSRF':'invalid'})[0] == 403, 'authenticated invalid CSRF rejected')
            check(request('/api/work-orders','POST',{},origin_value='https://evil.example')[0] == 403, 'cross-origin mutation rejected despite valid CSRF')
            check(request(auth+'/session',override={'Authorization':'Basic invalid'})[0] == 401, 'Basic authorization rejected in browser profile')
            check(request(auth+'/logout','POST',xsrf=False)[0] == 403, 'logout missing CSRF rejected')
            invalid = request('/api/work-orders','POST',{'title':' ','priority':'HIGH','requesterEmail':'invalid'})
            check(invalid[0] == 400 and {'title','requesterEmail'} <= invalid[2]['validationErrors'].keys(), 'backend field validation with authenticated CSRF')
            fixture = {'title':'M4C API security fixture','priority':'HIGH','requesterEmail':'fixture@example.com'}
            created_order = request('/api/work-orders','POST',fixture)
            check(created_order[0] == 201, 'authorized CSRF create persisted')
            order_id = int(created_order[2]['id']); path = '/api/work-orders/'+str(order_id)
            check(request(path+'/status','PATCH',{'status':'COMPLETED'})[0] == 200, 'authorized CSRF status update')
            check(request(path)[2]['status'] == 'COMPLETED', 'reload retrieves persisted status')
            sql = run(compose+['exec','-T','db','sh','-c',
                'psql -U "$POSTGRES_USER" -d workorder -Atc \'SELECT status FROM work_orders WHERE id='+str(order_id)+";\'"]).stdout.strip()
            check(sql == 'COMPLETED', 'independent PostgreSQL status confirmation')
            check(request(auth+'/logout','POST')[0] == 204, 'logout accepted with CSRF')
            check(cookie('M4CSESSION') is None and request(auth+'/session')[2] == {'authenticated':False}, 'logout removes cookie and invalidates session')
            # Replay the old server session ID without logging it; it must no longer authenticate.
            check(request(auth+'/session',override={'Cookie':'M4CSESSION='+active_session})[2] == {'authenticated':False}, 'old session replay after logout rejected')
            check(request(path+'/status','PATCH',{'status':'OPEN'})[0] == 401, 'post-logout write rejected')
            if args.expiry:
                request(auth+'/csrf'); check(request(auth+'/login','POST',credentials)[0] == 204, 'expiry fixture login')
                request(auth+'/csrf')
                print('Waiting 70 seconds without session requests for real Tomcat inactivity expiry...', flush=True)
                # CLI harness only; callers remain responsive by yielding process output.
                time.sleep(70)
                check(request(path+'/status','PATCH',{'status':'OPEN'})[0] == 401, 'real session inactivity expiry rejects writes')
                check(request(auth+'/session')[2] == {'authenticated':False}, 'expired session reports anonymous')
            evidence.update(apiFixtureId=order_id, sessionValuesRedacted=True, passwordsRedacted=True, expiryVerified=args.expiry)
        finally:
            if created: run(compose+['down','--volumes','--remove-orphans'])
        if args.evidence:
            (repo/'docs/M4C_API_SECURITY_EVIDENCE.json').write_text(json.dumps(evidence,indent=2)+'\n')

if __name__ == '__main__': main()
