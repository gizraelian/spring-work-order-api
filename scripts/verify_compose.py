"""Verify only a new, isolated local Compose project; no production endpoints or credentials."""
import base64
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import tempfile
import urllib.error
import urllib.request


def main():
    repo = Path(__file__).resolve().parents[1]
    project = "workorder-m3-" + secrets.token_hex(6)
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    env = dict(os.environ)
    for key in list(env):
        if key.startswith("LOCAL_") or key in ("COMPOSE_FILE", "COMPOSE_PROFILES", "COMPOSE_PROJECT_NAME"):
            del env[key]
    env.update(LOCAL_DB_USERNAME="m3_fixture", LOCAL_DB_PASSWORD=secrets.token_hex(24),
               LOCAL_APP_USERNAME="m3_fixture", LOCAL_APP_PASSWORD=secrets.token_hex(24),
               LOCAL_API_PORT=str(port), LOCAL_DEMO_SEED="false")
    secret_values = [env["LOCAL_DB_PASSWORD"], env["LOCAL_APP_PASSWORD"]]

    def run(args, *, check=True, environment=None):
        result = subprocess.run(args, cwd=repo, env=environment or env, capture_output=True,
                                text=True, timeout=240)
        if check and result.returncode:
            output = result.stdout + result.stderr
            for value in secret_values:
                output = output.replace(value, "[redacted]")
            raise RuntimeError(f"Command failed ({result.returncode}): {args[0:3]}\n{output}")
        return result

    with tempfile.TemporaryDirectory(prefix="workorder-m3-") as temporary:
        empty_env = Path(temporary) / "empty.env"
        empty_env.write_text("", encoding="utf-8")
        compose = ["docker", "compose", "--env-file", str(empty_env), "-p", project]
        # Require all four variables even when dev has API fallback defaults.
        for key in ("LOCAL_DB_USERNAME", "LOCAL_DB_PASSWORD", "LOCAL_APP_USERNAME", "LOCAL_APP_PASSWORD"):
            incomplete = env.copy()
            incomplete[key] = ""
            result = run(compose + ["config", "--quiet"], check=False, environment=incomplete)
            if result.returncode == 0 or key not in result.stderr:
                raise AssertionError("Compose did not reject missing " + key)
        print("PASS: all four missing/blank credentials rejected before startup")
        volume = project + "_postgres-data"
        if run(["docker", "volume", "inspect", volume], check=False).returncode == 0:
            raise RuntimeError("Refusing to use a pre-existing volume")
        created = False
        base = f"http://127.0.0.1:{port}"
        # Ignore system proxies; every request stays on the loopback Compose port.
        client = urllib.request.build_opener(urllib.request.ProxyHandler({}))

        def request(path, *, method="GET", body=None, authenticated=False):
            headers = {"Accept": "application/json"}
            if authenticated:
                token = base64.b64encode((env["LOCAL_APP_USERNAME"] + ":" + env["LOCAL_APP_PASSWORD"]).encode()).decode()
                headers["Authorization"] = "Basic " + token
            data = None if body is None else json.dumps(body).encode()
            if data is not None:
                headers["Content-Type"] = "application/json"
            req = urllib.request.Request(base + path, data=data, method=method, headers=headers)
            try:
                with client.open(req, timeout=15) as response:
                    return response.status, response.headers, json.loads(response.read())
            except urllib.error.HTTPError as error:
                return error.code, error.headers, None

        def sql(query):
            return run(compose + ["exec", "-T", "db", "psql", "-U", env["LOCAL_DB_USERNAME"],
                                  "-d", "workorder", "-Atc", query]).stdout.strip()

        try:
            created = True
            run(compose + ["up", "-d", "--no-build", "--wait", "--wait-timeout", "180"])
            for service in ("db", "api"):
                container = run(compose + ["ps", "-q", service]).stdout.strip()
                health = run(["docker", "inspect", "--format", "{{.State.Health.Status}}", container]).stdout.strip()
                if health != "healthy":
                    raise AssertionError(service + " is not healthy")
            api = run(compose + ["ps", "-q", "api"]).stdout.strip()
            db = run(compose + ["ps", "-q", "db"]).stdout.strip()
            bindings = json.loads(run(["docker", "inspect", "--format", "{{json .HostConfig.PortBindings}}", api]).stdout)
            if bindings != {"8080/tcp": [{"HostIp": "127.0.0.1", "HostPort": str(port)}]}:
                raise AssertionError("API ports are not exclusively loopback")
            if json.loads(run(["docker", "inspect", "--format", "{{json .HostConfig.PortBindings}}", db]).stdout):
                raise AssertionError("PostgreSQL has a published host port")
            if run(["docker", "inspect", "--format", "{{.Config.User}}", api]).stdout.strip() != "10001:10001":
                raise AssertionError("API is not running as the intended non-root user")
            run(compose + ["exec", "-T", "api", "curl", "--version"])
            if request("/actuator/health")[2] != {"status": "UP"}:
                raise AssertionError("Unexpected health response")
            if request("/api/work-orders")[2]["page"]["totalElements"] != 0:
                raise AssertionError("Fresh database was automatically seeded")
            if sql("SELECT version || ':' || success FROM flyway_schema_history") != "1:true":
                raise AssertionError("Unexpected migration history")
            print("PASS: healthy non-root API; localhost-only API port; DB unpublished; fresh V1; no seed")
            body = {"title": "M3 persistence fixture", "priority": "HIGH", "requesterEmail": "fixture@example.com"}
            if request("/api/work-orders", method="POST", body=body)[0] != 401:
                raise AssertionError("Unauthenticated write accepted")
            if request("/api/work-orders", method="POST", body={"title": " "}, authenticated=True)[0] != 400:
                raise AssertionError("Invalid write accepted")
            status, headers, order = request("/api/work-orders", method="POST", body=body, authenticated=True)
            if status != 201 or headers.get("Location") != f"/api/work-orders/{order['id']}":
                raise AssertionError("Create contract mismatch")
            path = f"/api/work-orders/{order['id']}"
            if request(path + "/status", method="PATCH", body={"status": "IN_PROGRESS"}, authenticated=True)[0] != 200:
                raise AssertionError("Status update failed")
            page = request("/api/work-orders?status=IN_PROGRESS&priority=HIGH&page=0&size=1")[2]
            if page["page"]["totalElements"] != 1 or page["content"][0]["id"] != order["id"]:
                raise AssertionError("Combined filter/page failed")
            print("PASS: unauthorized/invalid writes; create Location; status update; combined filter/page")
            installed = sql("SELECT installed_on::text FROM flyway_schema_history WHERE version='1'")
            table_oid = sql("SELECT 'work_orders'::regclass::oid")
            for mode in ("restart", "recreate"):
                if mode == "restart":
                    run(compose + ["restart"])
                else:
                    run(compose + ["down"])
                run(compose + ["up", "-d", "--no-build", "--wait", "--wait-timeout", "180"])
                if request(path)[2]["status"] != "IN_PROGRESS":
                    raise AssertionError("Record did not survive " + mode)
                if sql("SELECT installed_on::text FROM flyway_schema_history WHERE version='1'") != installed:
                    raise AssertionError("Migration history changed during " + mode)
                if sql("SELECT 'work_orders'::regclass::oid") != table_oid:
                    raise AssertionError("Table recreated during " + mode)
                print("PASS: persisted row, table identity and migration history survive " + mode)
        finally:
            if created:
                run(compose + ["down", "--volumes", "--remove-orphans"])
                print("Cleaned only isolated verification project: " + project)


if __name__ == "__main__":
    main()
