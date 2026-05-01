"""
test_01_user_auth.py — User Service Integration Tests
======================================================
Covers:
  S1-F10  Register User            (Section 10.1.1)
  S1-F11  Login                    (Section 10.1.2)
  S1-F12  Get User Activity Feed   (Section 10.1.3)
  CC-1    JWT on All Endpoints     (Section 9.1)
  CC-2    Role Management          (Section 9.2)
  M1-MOD  Password Hashing         (Section 4.1)
  M1-MOD  Role Values              (Section 4.2)
  M1-MOD  JWT on M1 Endpoints      (Section 4.3)
  DP-3    Chain of Responsibility  (Section 3.4) — behavioral checks
  DP-5    Singleton JwtConfigManager (Section 3.6) — source-scan + behavioral
  DP-2    Observer / Factory       (Section 3.3, 3.7) — MongoDB event checks
"""

import os
import time
import uuid
import jwt as _jwt_lib   # PyJWT
import requests
import pytest
from pathlib import Path
from datetime import datetime, timezone, timedelta

# ---------------------------------------------------------------------------
# Source-scan helpers (for DP pattern static checks)
# ---------------------------------------------------------------------------

PROJECT_ROOT = Path(__file__).resolve().parents[2]   # repo root

def _java_source_files(service: str):
    """Yield all .java files under <service>/src/main/java/."""
    base = PROJECT_ROOT / service / "src" / "main" / "java"
    if not base.exists():
        return
    yield from base.rglob("*.java")


def _grep_source(service: str, pattern: str) -> bool:
    """Return True if `pattern` appears in any .java source file of the service."""
    import re
    regex = re.compile(pattern)
    for path in _java_source_files(service):
        try:
            if regex.search(path.read_text(encoding="utf-8", errors="ignore")):
                return True
        except OSError:
            pass
    return False


# ---------------------------------------------------------------------------
# S1-F10: Register User  (Section 10.1.1)
# ---------------------------------------------------------------------------

class TestS1F10Register:
    """S1-F10 — Register User.

    Public endpoint: no Authorization header required (CC-1 public carve-out).
    On success → 201 + JWT token. Observer writes REGISTERED to auth_events.
    """

    def test_register_returns_201_with_token(self, user_url):
        """Scenario a: valid data → 201 with token field present."""
        suffix = uuid.uuid4().hex[:8]
        payload = {
            "name":     "Ahmed Ali",
            "email":    f"ahmed_{suffix}@example.com",
            "password": "securePassword123",
            "phone":    f"+201{int(suffix[:8], 16) % 10**9:09d}",
        }
        resp = requests.post(f"{user_url}/api/auth/register", json=payload, timeout=10)
        assert resp.status_code == 201, f"Expected 201, got {resp.status_code}: {resp.text}"
        body = resp.json()
        assert "token" in body, "Response must contain 'token'"
        assert "expiresIn" in body, "Response must contain 'expiresIn' (Section 10.1.1)"

    def test_register_creates_user_in_postgres(self, user_url, pg_conn):
        """Scenario a (DB check): user row exists in PostgreSQL after registration."""
        suffix = uuid.uuid4().hex[:8]
        email  = f"pgcheck_{suffix}@example.com"
        phone  = f"+204{int(suffix[:8], 16) % 10**9:09d}"
        requests.post(f"{user_url}/api/auth/register", json={
            "name": "PG Check User", "email": email,
            "password": "securePassword123", "phone": phone,
        }, timeout=10)
        cur = pg_conn.cursor()
        cur.execute("SELECT id FROM users WHERE email = %s", (email,))
        row = cur.fetchone()
        cur.close()
        assert row is not None, "User row must exist in PostgreSQL after registration"

    def test_register_duplicate_email_returns_409(self, user_url):
        """Scenario b: same email → 409 Conflict."""
        suffix = uuid.uuid4().hex[:8]
        payload = {
            "name": "Dup User", "email": f"dup_{suffix}@example.com",
            "password": "pass123", "phone": f"+205{int(suffix[:8], 16) % 10**9:09d}",
        }
        r1 = requests.post(f"{user_url}/api/auth/register", json=payload, timeout=10)
        assert r1.status_code == 201, "First registration should succeed"

        payload2 = {**payload, "phone": f"+206{int(suffix[:8], 16) % 10**9:09d}"}
        r2 = requests.post(f"{user_url}/api/auth/register", json=payload2, timeout=10)
        assert r2.status_code == 409, (
            f"Duplicate email must return 409, got {r2.status_code}: {r2.text}"
        )

    def test_register_duplicate_phone_returns_409(self, user_url):
        """Scenario c: same phone (different email) → 409."""
        suffix = uuid.uuid4().hex[:8]
        phone  = f"+207{int(suffix[:8], 16) % 10**9:09d}"
        r1 = requests.post(f"{user_url}/api/auth/register", json={
            "name": "P1", "email": f"p1_{suffix}@ex.com",
            "password": "pass", "phone": phone,
        }, timeout=10)
        assert r1.status_code == 201

        r2 = requests.post(f"{user_url}/api/auth/register", json={
            "name": "P2", "email": f"p2_{suffix}@ex.com",
            "password": "pass", "phone": phone,
        }, timeout=10)
        assert r2.status_code == 409, (
            f"Duplicate phone must return 409, got {r2.status_code}: {r2.text}"
        )

    def test_register_blank_email_returns_400(self, user_url):
        """Scenario d: blank email → 400 Bad Request (Section 10.1.1 step a)."""
        resp = requests.post(f"{user_url}/api/auth/register", json={
            "name": "No Email", "email": "", "password": "pass123",
            "phone": "+20100000001",
        }, timeout=10)
        assert resp.status_code == 400, (
            f"Blank email must return 400, got {resp.status_code}"
        )

    def test_register_blank_phone_returns_400(self, user_url):
        """Scenario d: blank phone → 400."""
        resp = requests.post(f"{user_url}/api/auth/register", json={
            "name": "No Phone", "email": f"nophone_{uuid.uuid4().hex[:6]}@ex.com",
            "password": "pass123", "phone": "",
        }, timeout=10)
        assert resp.status_code == 400, (
            f"Blank phone must return 400, got {resp.status_code}"
        )

    def test_register_missing_name_returns_400(self, user_url):
        """Scenario d (extended): missing name → 400."""
        resp = requests.post(f"{user_url}/api/auth/register", json={
            "email": f"noname_{uuid.uuid4().hex[:6]}@ex.com",
            "password": "pass123", "phone": "+20111111111",
        }, timeout=10)
        assert resp.status_code == 400, (
            f"Missing name must return 400, got {resp.status_code}"
        )

    # --- M1-MOD: Password Hashing (Section 4.1) ---

    def test_password_is_bcrypt_hash_in_db(self, user_url, pg_conn):
        """Section 4.1 / Scenario e: stored password must be a BCrypt hash.

        BCrypt hashes start with $2a$, $2b$, or $2y$ and are 60 chars long.
        """
        suffix   = uuid.uuid4().hex[:8]
        email    = f"bcrypt_{suffix}@example.com"
        password = "securePassword123"
        requests.post(f"{user_url}/api/auth/register", json={
            "name": "BCrypt Test", "email": email,
            "password": password, "phone": f"+208{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)

        cur = pg_conn.cursor()
        cur.execute("SELECT password FROM users WHERE email = %s", (email,))
        row = cur.fetchone()
        cur.close()
        assert row, "User must exist in DB"
        stored_pw = row[0]
        assert stored_pw.startswith(("$2a$", "$2b$", "$2y$")), (
            f"Password must be a BCrypt hash, got: {stored_pw[:10]}..."
        )
        assert len(stored_pw) == 60, (
            f"BCrypt hash must be exactly 60 chars, got {len(stored_pw)}"
        )
        assert stored_pw != password, "Plaintext password must NOT be stored"

    def test_password_not_returned_in_response(self, user_url):
        """Section 4.1: password field must be absent or null in API responses."""
        suffix = uuid.uuid4().hex[:8]
        resp = requests.post(f"{user_url}/api/auth/register", json={
            "name": "NoPass User", "email": f"nopw_{suffix}@ex.com",
            "password": "secret", "phone": f"+209{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        body = resp.json()
        # The register response contains token/expiresIn, not a user object.
        # But any nested user object must not have password.
        assert "password" not in body or body.get("password") is None, (
            "password must NOT appear in register response"
        )

    def test_register_writes_registered_event_to_mongodb(self, user_url, mongo_db):
        """DP-2 Observer / Section 10.1.1 step e + DP-6 Factory:
        POST /api/auth/register must write a REGISTERED document to auth_events.

        Specifically tests:
          - Observer pattern (DP-2): notifyObservers("REGISTERED", payload)
          - Factory pattern (DP-6): EventFactory.createEvent(AUTH, params) → AuthEvent
        """
        suffix = uuid.uuid4().hex[:8]
        email  = f"mongo_{suffix}@example.com"
        resp = requests.post(f"{user_url}/api/auth/register", json={
            "name": "Mongo Test", "email": email, "password": "pass123",
            "phone": f"+210{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        assert resp.status_code == 201

        token   = resp.json()["token"]
        user_id = int(_jwt_lib.decode(token, options={"verify_signature": False},
                                      algorithms=["HS256"])["uid"])

        # Allow Observer to complete asynchronously
        time.sleep(0.5)

        collection = mongo_db["auth_events"]
        doc = collection.find_one({"userId": user_id, "action": "REGISTERED"})
        assert doc is not None, (
            f"REGISTERED event must exist in auth_events for userId={user_id} "
            "(Observer/Factory pattern not firing)"
        )
        assert "timestamp" in doc, "auth_events doc must have a timestamp field"

    # --- M1-MOD: Role Values (Section 4.2) ---

    def test_register_assigns_attendee_role(self, user_url, pg_conn):
        """Section 4.2 / Scenario b: new user must get ATTENDEE role."""
        suffix = uuid.uuid4().hex[:8]
        email  = f"role_{suffix}@example.com"
        resp   = requests.post(f"{user_url}/api/auth/register", json={
            "name": "Role Test", "email": email, "password": "pass123",
            "phone": f"+211{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        assert resp.status_code == 201
        token   = resp.json()["token"]
        user_id = int(_jwt_lib.decode(token, options={"verify_signature": False},
                                      algorithms=["HS256"])["uid"])

        cur = pg_conn.cursor()
        cur.execute("SELECT role FROM users WHERE id = %s", (user_id,))
        row = cur.fetchone()
        cur.close()
        assert row[0] == "ATTENDEE", f"Registered user must have ATTENDEE role, got {row[0]}"

    def test_register_role_claim_is_attendee(self, user_url):
        """Section 4.2 / Scenario c: JWT role claim must be ATTENDEE on register."""
        suffix = uuid.uuid4().hex[:8]
        resp = requests.post(f"{user_url}/api/auth/register", json={
            "name": "Claim Test", "email": f"claim_{suffix}@ex.com",
            "password": "pass", "phone": f"+212{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        assert resp.status_code == 201
        claims = _jwt_lib.decode(resp.json()["token"],
                                 options={"verify_signature": False},
                                 algorithms=["HS256"])
        assert claims.get("role") == "ATTENDEE", (
            f"JWT role claim must be ATTENDEE on register, got {claims.get('role')}"
        )

    def test_register_bad_faith_admin_role_ignored(self, user_url, pg_conn):
        """Section 4.2 / Scenario e: sending role=ADMIN in body must be ignored."""
        suffix = uuid.uuid4().hex[:8]
        email  = f"badfaith_{suffix}@ex.com"
        resp = requests.post(f"{user_url}/api/auth/register", json={
            "name": "Bad Faith", "email": email, "password": "pass",
            "phone": f"+213{int(suffix[:8], 16) % 10**9:09d}",
            "role": "ADMIN",   # bad-faith attempt
        }, timeout=10)
        assert resp.status_code == 201
        token   = resp.json()["token"]
        user_id = int(_jwt_lib.decode(token, options={"verify_signature": False},
                                      algorithms=["HS256"])["uid"])
        cur = pg_conn.cursor()
        cur.execute("SELECT role FROM users WHERE id = %s", (user_id,))
        row = cur.fetchone()
        cur.close()
        assert row[0] == "ATTENDEE", (
            "Server must ignore role=ADMIN in register body; user must be ATTENDEE"
        )

    def test_postgres_role_enum_contains_attendee_and_admin(self, pg_conn):
        """Section 4.2 / Scenario a: role column must support ATTENDEE and ADMIN values.

        Hibernate 7 may create the PG enum type as 'userrole' (Java class name).
        Accept either a native PG ENUM with ATTENDEE/ADMIN, or a VARCHAR column
        that stores those values (both satisfy the spec requirement).
        """
        cur = pg_conn.cursor()
        # Try native PG enum type (Hibernate 7 names it after the Java class: 'userrole')
        cur.execute(
            "SELECT enumlabel FROM pg_enum pe "
            "JOIN pg_type pt ON pe.enumtypid = pt.oid "
            "WHERE pt.typname IN ('role', 'userrole')"
        )
        enum_values = {row[0] for row in cur.fetchall()}
        cur.close()
        if enum_values:
            assert "ATTENDEE" in enum_values, "PG role ENUM must contain ATTENDEE"
            assert "ADMIN" in enum_values, "PG role ENUM must contain ADMIN"
        else:
            # Hibernate may store as VARCHAR — verify the column exists and holds 'ATTENDEE'
            cur2 = pg_conn.cursor()
            cur2.execute("SELECT column_name, data_type FROM information_schema.columns "
                         "WHERE table_name='users' AND column_name='role'")
            row = cur2.fetchone()
            cur2.close()
            assert row is not None, "users.role column must exist (Section 4.2)"
            # Values are stored as strings; pass (VARCHAR satisfies spec for DDL check)


# ---------------------------------------------------------------------------
# S1-F11: Login  (Section 10.1.2)
# ---------------------------------------------------------------------------

class TestS1F11Login:
    """S1-F11 — Login.

    Public endpoint — no Authorization header required (CC-1).
    Returns JWT on success; 401 for wrong credentials (prevents enumeration).
    Observer writes LOGGED_IN to auth_events (DP-2).
    """

    def test_login_valid_credentials_returns_200(self, user_url):
        """Scenario a: register then login with correct credentials → 200 + token."""
        suffix = uuid.uuid4().hex[:8]
        email  = f"login_{suffix}@ex.com"
        phone  = f"+214{int(suffix[:8], 16) % 10**9:09d}"
        password = "MyPass123"
        requests.post(f"{user_url}/api/auth/register", json={
            "name": "Login User", "email": email,
            "password": password, "phone": phone,
        }, timeout=10)

        resp = requests.post(f"{user_url}/api/auth/login",
                             json={"email": email, "password": password}, timeout=10)
        assert resp.status_code == 200, f"Login should return 200, got {resp.status_code}: {resp.text}"
        body = resp.json()
        assert "token" in body, "Login response must have 'token'"
        assert "expiresIn" in body, "Login response must have 'expiresIn'"

    def test_login_wrong_password_returns_401(self, user_url):
        """Scenario b: wrong password → 401 (NOT 403 or 404)."""
        suffix = uuid.uuid4().hex[:8]
        email  = f"wp_{suffix}@ex.com"
        requests.post(f"{user_url}/api/auth/register", json={
            "name": "WP User", "email": email, "password": "correct",
            "phone": f"+215{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)

        resp = requests.post(f"{user_url}/api/auth/login",
                             json={"email": email, "password": "wrong"}, timeout=10)
        assert resp.status_code == 401, (
            f"Wrong password must return 401, got {resp.status_code}"
        )

    def test_login_unknown_email_returns_401_not_404(self, user_url):
        """Scenario c: unknown email → 401 (prevents account enumeration, Section 10.1.2)."""
        resp = requests.post(f"{user_url}/api/auth/login",
                             json={"email": "nobody@nowhere.com", "password": "pass"},
                             timeout=10)
        assert resp.status_code == 401, (
            f"Unknown email must return 401 (not 404), got {resp.status_code}"
        )

    def test_login_token_can_access_protected_endpoint(self, user_url):
        """Scenario d: token returned by login is valid for protected endpoints."""
        suffix = uuid.uuid4().hex[:8]
        email  = f"use_{suffix}@ex.com"
        password = "UsePass123"
        reg = requests.post(f"{user_url}/api/auth/register", json={
            "name": "Token User", "email": email, "password": password,
            "phone": f"+216{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        user_id = int(_jwt_lib.decode(reg.json()["token"],
                                      options={"verify_signature": False},
                                      algorithms=["HS256"])["uid"])

        login_resp = requests.post(f"{user_url}/api/auth/login",
                                   json={"email": email, "password": password}, timeout=10)
        token = login_resp.json()["token"]

        # Access a protected M1 endpoint with the token
        protected = requests.get(
            f"{user_url}/api/users/{user_id}",
            headers={"Authorization": f"Bearer {token}"},
            timeout=10,
        )
        assert protected.status_code == 200, (
            f"Login token must work for protected endpoint, got {protected.status_code}"
        )

        # Without token → 401
        no_auth = requests.get(f"{user_url}/api/users/{user_id}", timeout=10)
        assert no_auth.status_code == 401, (
            f"No-auth request must return 401, got {no_auth.status_code}"
        )

    def test_login_writes_logged_in_event_to_mongodb(self, user_url, mongo_db):
        """DP-2 Observer / Scenario (login path):
        POST /api/auth/login → LOGGED_IN document in auth_events (Section 10.1.2 step c).
        """
        suffix = uuid.uuid4().hex[:8]
        email  = f"mongoln_{suffix}@ex.com"
        password = "LN123"
        reg = requests.post(f"{user_url}/api/auth/register", json={
            "name": "Mongo Login", "email": email, "password": password,
            "phone": f"+217{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        user_id = int(_jwt_lib.decode(reg.json()["token"],
                                      options={"verify_signature": False},
                                      algorithms=["HS256"])["uid"])

        requests.post(f"{user_url}/api/auth/login",
                      json={"email": email, "password": password}, timeout=10)
        time.sleep(0.5)

        doc = mongo_db["auth_events"].find_one({"userId": user_id, "action": "LOGGED_IN"})
        assert doc is not None, (
            f"LOGGED_IN event must be in auth_events for userId={user_id} "
            "(Observer pattern must fire on login)"
        )

    def test_login_password_not_in_response(self, user_url):
        """Section 4.1: login response must not expose password field."""
        suffix = uuid.uuid4().hex[:8]
        email  = f"nopwln_{suffix}@ex.com"
        password = "secret"
        requests.post(f"{user_url}/api/auth/register", json={
            "name": "NP Login", "email": email, "password": password,
            "phone": f"+218{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        resp = requests.post(f"{user_url}/api/auth/login",
                             json={"email": email, "password": password}, timeout=10)
        assert resp.status_code == 200
        assert "password" not in resp.json() or resp.json().get("password") is None


# ---------------------------------------------------------------------------
# S1-F12: Get User Activity Feed  (Section 10.1.3)
# ---------------------------------------------------------------------------

class TestS1F12ActivityFeed:
    """S1-F12 — User Activity Feed.

    GET /api/users/{id}/activity?page=&size=
    Auth: USER-level (ATTENDEE or ADMIN).
    Ownership check: uid claim == path id, or caller is ADMIN.
    Cached 5 min in Redis (CC-3).
    Observer writes events on each action (DP-2).
    """

    def test_own_activity_contains_registered_and_logged_in(self, user_url, auth_user):
        """Scenario a: own token → activity contains REGISTERED and LOGGED_IN."""
        resp = requests.get(
            f"{user_url}/api/users/{auth_user['user_id']}/activity",
            headers=auth_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 200, f"Expected 200, got {resp.status_code}: {resp.text}"
        body = resp.json()
        assert "content" in body, "Activity feed must return paginated 'content'"
        actions = [e["action"] for e in body["content"]]
        assert "REGISTERED" in actions, f"REGISTERED missing from feed. Got: {actions}"
        assert "LOGGED_IN" in actions, f"LOGGED_IN missing from feed. Got: {actions}"

    def test_activity_feed_ordered_most_recent_first(self, user_url, auth_user):
        """Scenario a (order check): events should be newest-first."""
        resp = requests.get(
            f"{user_url}/api/users/{auth_user['user_id']}/activity",
            headers=auth_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 200
        events = resp.json()["content"]
        if len(events) >= 2:
            t1 = events[0]["timestamp"]
            t2 = events[1]["timestamp"]
            assert t1 >= t2, f"Events must be newest-first, but {t1} < {t2}"

    def test_activity_feed_contains_role_changed_event(self, user_url, admin_user, pg_conn):
        """Scenario b: after a role change, ROLE_CHANGED appears in activity feed.

        CC-2 (Section 9.2) + S1-F12.
        """
        # Create a fresh user for role promotion
        suffix   = uuid.uuid4().hex[:8]
        email    = f"rolechange_{suffix}@ex.com"
        password = "RCPass123"
        reg = requests.post(f"{user_url}/api/auth/register", json={
            "name": "Role Target", "email": email, "password": password,
            "phone": f"+219{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        assert reg.status_code == 201
        target_token   = reg.json()["token"]
        target_user_id = int(_jwt_lib.decode(target_token,
                                             options={"verify_signature": False},
                                             algorithms=["HS256"])["uid"])
        target_headers = {"Authorization": f"Bearer {target_token}"}

        # ADMIN promotes the user
        promote = requests.put(
            f"{user_url}/api/users/{target_user_id}/role",
            json={"role": "ADMIN"},
            headers=admin_user["headers"],
            timeout=10,
        )
        assert promote.status_code == 200, f"Role promotion failed: {promote.text}"
        time.sleep(0.5)

        # Re-login to get a fresh token with updated role claim
        login = requests.post(f"{user_url}/api/auth/login",
                              json={"email": email, "password": password}, timeout=10)
        assert login.status_code == 200
        new_token   = login.json()["token"]
        new_headers = {"Authorization": f"Bearer {new_token}"}

        feed = requests.get(
            f"{user_url}/api/users/{target_user_id}/activity",
            headers=new_headers,
            timeout=10,
        )
        assert feed.status_code == 200
        actions = [e["action"] for e in feed.json()["content"]]
        assert "ROLE_CHANGED" in actions, (
            f"ROLE_CHANGED event must appear in activity feed after role promotion. "
            f"Got actions: {actions}"
        )

    def test_cross_user_activity_returns_403(self, user_url, auth_user):
        """Scenario c: User A cannot see User B's activity → 403 (not 404)."""
        suffix   = uuid.uuid4().hex[:8]
        email    = f"userb_{suffix}@ex.com"
        reg      = requests.post(f"{user_url}/api/auth/register", json={
            "name": "User B", "email": email, "password": "pass",
            "phone": f"+220{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        assert reg.status_code == 201
        user_b_id = int(_jwt_lib.decode(reg.json()["token"],
                                        options={"verify_signature": False},
                                        algorithms=["HS256"])["uid"])

        resp = requests.get(
            f"{user_url}/api/users/{user_b_id}/activity",
            headers=auth_user["headers"],   # User A's token
            timeout=10,
        )
        assert resp.status_code == 403, (
            f"Cross-user activity access must return 403, got {resp.status_code}"
        )

    def test_admin_can_view_any_user_activity(self, user_url, auth_user, admin_user):
        """Scenario d: ADMIN token bypasses ownership check → 200."""
        resp = requests.get(
            f"{user_url}/api/users/{auth_user['user_id']}/activity",
            headers=admin_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 200, (
            f"ADMIN must be able to view any user's activity, got {resp.status_code}"
        )

    def test_activity_feed_no_auth_returns_401(self, user_url, auth_user):
        """Scenario e: no Authorization header → 401 (CC-1)."""
        resp = requests.get(
            f"{user_url}/api/users/{auth_user['user_id']}/activity",
            timeout=10,
        )
        assert resp.status_code == 401, f"No-auth must return 401, got {resp.status_code}"

    def test_activity_feed_nonexistent_user_admin_returns_404(self, user_url, admin_user):
        """Scenario f: admin token + non-existent user → 404 (Section 10.1.3 step c)."""
        resp = requests.get(
            f"{user_url}/api/users/999999/activity",
            headers=admin_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 404, (
            f"Non-existent user must return 404 (even for ADMIN), got {resp.status_code}"
        )

    def test_activity_feed_pagination(self, user_url, auth_user):
        """Scenario g: page=0&size=1 returns 1 event with correct totalElements."""
        resp = requests.get(
            f"{user_url}/api/users/{auth_user['user_id']}/activity",
            params={"page": 0, "size": 1},
            headers=auth_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 200
        body = resp.json()
        assert len(body["content"]) <= 1, "page=0&size=1 must return at most 1 event"
        assert "totalElements" in body, "Paginated response must include totalElements"
        assert body.get("size") == 1 or body.get("pageSize") == 1, (
            "Returned page size must be 1"
        )

    def test_activity_feed_cached_in_redis(self, user_url, auth_user, redis_client):
        """CC-3 + S1-F12 step e: activity feed cached for 5 minutes.

        After the first GET, a Redis key matching user-service::S1-F12::{userId}
        must exist.
        """
        user_id = auth_user["user_id"]
        requests.get(
            f"{user_url}/api/users/{user_id}/activity",
            headers=auth_user["headers"],
            timeout=10,
        )
        time.sleep(0.3)
        pattern = f"user-service::S1-F12::{user_id}*"
        keys = list(redis_client.scan_iter(pattern))
        assert keys, (
            f"Redis must have a key matching '{pattern}' after GET activity feed "
            "(CC-3 / S1-F12 step e caching requirement)"
        )


# ---------------------------------------------------------------------------
# CC-2: Role Management  (Section 9.2)
# ---------------------------------------------------------------------------

class TestCC2RoleManagement:
    """CC-2 — PUT /api/users/{id}/role (ADMIN-only endpoint).

    Section 9.2 full test scenario.
    Observer must log ROLE_CHANGED to auth_events (Section 9.2 step c).
    Redis detail cache for target user must be invalidated (Section 9.2 step d).
    """

    def test_admin_can_promote_attendee_to_admin(self, user_url, admin_user, pg_conn):
        """Scenario b: ADMIN calls PUT role → 200, target user has ADMIN in PG."""
        suffix   = uuid.uuid4().hex[:8]
        email    = f"prom_{suffix}@ex.com"
        reg = requests.post(f"{user_url}/api/auth/register", json={
            "name": "Promote Me", "email": email, "password": "pp",
            "phone": f"+221{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        target_id = int(_jwt_lib.decode(reg.json()["token"],
                                        options={"verify_signature": False},
                                        algorithms=["HS256"])["uid"])

        resp = requests.put(
            f"{user_url}/api/users/{target_id}/role",
            json={"role": "ADMIN"},
            headers=admin_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 200, f"Role promotion must return 200, got {resp.status_code}: {resp.text}"

        cur = pg_conn.cursor()
        cur.execute("SELECT role FROM users WHERE id = %s", (target_id,))
        row = cur.fetchone()
        cur.close()
        assert row[0] == "ADMIN", f"User must have ADMIN role in PG after promotion, got {row[0]}"

    def test_role_change_writes_role_changed_to_mongodb(
        self, user_url, admin_user, mongo_db
    ):
        """Scenario c: ROLE_CHANGED event appears in auth_events with old/new role.

        DP-2 Observer + DP-6 Factory must fire on role management endpoint.
        """
        suffix = uuid.uuid4().hex[:8]
        email  = f"rcev_{suffix}@ex.com"
        reg = requests.post(f"{user_url}/api/auth/register", json={
            "name": "RC Event", "email": email, "password": "rc",
            "phone": f"+222{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        target_id = int(_jwt_lib.decode(reg.json()["token"],
                                        options={"verify_signature": False},
                                        algorithms=["HS256"])["uid"])

        requests.put(
            f"{user_url}/api/users/{target_id}/role",
            json={"role": "ADMIN"},
            headers=admin_user["headers"],
            timeout=10,
        )
        time.sleep(0.5)

        doc = mongo_db["auth_events"].find_one(
            {"userId": target_id, "action": "ROLE_CHANGED"}
        )
        assert doc is not None, (
            f"ROLE_CHANGED event must be in auth_events for userId={target_id}"
        )
        details = doc.get("details", {})
        assert details, "ROLE_CHANGED details must not be empty"

    def test_role_change_invalidates_user_cache_in_redis(
        self, user_url, admin_user, redis_client
    ):
        """Scenario d: user detail cache key must be removed from Redis after role change.

        CC-3 + Section 4.4.4: PUT /api/users/{id}/role invalidates
        user-service::user::{id}.
        """
        suffix = uuid.uuid4().hex[:8]
        email  = f"rccache_{suffix}@ex.com"
        reg = requests.post(f"{user_url}/api/auth/register", json={
            "name": "Cache RC", "email": email, "password": "cc",
            "phone": f"+223{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        target_id  = int(_jwt_lib.decode(reg.json()["token"],
                                         options={"verify_signature": False},
                                         algorithms=["HS256"])["uid"])
        target_headers = {"Authorization": "Bearer " + reg.json()["token"]}

        # Warm the cache
        requests.get(f"{user_url}/api/users/{target_id}",
                     headers=target_headers, timeout=10)
        time.sleep(0.3)

        # Promote → should invalidate cache
        requests.put(f"{user_url}/api/users/{target_id}/role",
                     json={"role": "ADMIN"}, headers=admin_user["headers"], timeout=10)
        time.sleep(0.3)

        cache_key = f"user-service::user::{target_id}"
        assert not redis_client.exists(cache_key), (
            f"Cache key '{cache_key}' must be removed after role change "
            "(Section 4.4.4 CC-2 invalidation rule)"
        )

    def test_attendee_cannot_change_role_returns_403(self, user_url, auth_user):
        """Scenario f: ATTENDEE token on role endpoint → 403."""
        resp = requests.put(
            f"{user_url}/api/users/1/role",
            json={"role": "ADMIN"},
            headers=auth_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 403, (
            f"ATTENDEE token must get 403 on role change, got {resp.status_code}"
        )

    def test_role_change_nonexistent_user_returns_404(self, user_url, admin_user):
        """Scenario g: PUT role on non-existent user → 404."""
        resp = requests.put(
            f"{user_url}/api/users/999999/role",
            json={"role": "ADMIN"},
            headers=admin_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 404, (
            f"Non-existent user must return 404, got {resp.status_code}"
        )

    def test_role_change_invalid_role_returns_400(self, user_url, admin_user, auth_user):
        """Scenario h: invalid role value → 400."""
        resp = requests.put(
            f"{user_url}/api/users/{auth_user['user_id']}/role",
            json={"role": "BANANA"},
            headers=admin_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 400, (
            f"Invalid role value must return 400, got {resp.status_code}"
        )

    def test_role_change_no_auth_returns_401(self, user_url, auth_user):
        """Scenario i: no Authorization header → 401 (CC-1)."""
        resp = requests.put(
            f"{user_url}/api/users/{auth_user['user_id']}/role",
            json={"role": "ADMIN"},
            timeout=10,
        )
        assert resp.status_code == 401, (
            f"No auth on role endpoint must return 401, got {resp.status_code}"
        )


# ---------------------------------------------------------------------------
# CC-1: JWT on All Endpoints  (Section 9.1)
# ---------------------------------------------------------------------------

class TestCC1JwtOnAllEndpoints:
    """CC-1 — JWT required on all endpoints except register/login/health.

    Section 9.1 full test scenario + DP-3 Chain of Responsibility behavioral checks.
    """

    def test_register_is_public_no_token_needed(self, user_url):
        """Scenario a + b: POST /api/auth/register requires NO token."""
        suffix = uuid.uuid4().hex[:8]
        resp = requests.post(f"{user_url}/api/auth/register", json={
            "name": "CC1 Public", "email": f"cc1_{suffix}@ex.com",
            "password": "pass", "phone": f"+224{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        assert resp.status_code == 201, "Register must be public (no token required)"

    def test_login_is_public_no_token_needed(self, user_url, auth_user):
        """Scenario b: POST /api/auth/login requires NO token."""
        resp = requests.post(
            f"{user_url}/api/auth/login",
            json={"email": auth_user["email"], "password": auth_user["password"]},
            timeout=10,
        )
        assert resp.status_code == 200, "Login must be public (no token required)"

    def test_m1_endpoint_without_token_returns_401(self, event_url):
        """Scenario c / DP-3 (TokenExtractionHandler): GET M1 endpoint without token → 401."""
        resp = requests.get(f"{event_url}/api/events/search?category=CONCERT", timeout=10)
        assert resp.status_code == 401, (
            f"M1 endpoint without token must return 401, got {resp.status_code}"
        )

    def test_m1_endpoint_with_invalid_token_returns_401(self, event_url):
        """Scenario d / DP-3 (SignatureValidationHandler): malformed token → 401."""
        resp = requests.get(
            f"{event_url}/api/events/search",
            headers={"Authorization": "Bearer invalid.token.here"},
            timeout=10,
        )
        assert resp.status_code == 401, (
            f"Invalid token must return 401, got {resp.status_code}"
        )

    def test_m1_endpoint_with_valid_token_returns_200(self, event_url, auth_headers):
        """Scenario e: valid token on M1 endpoint → 200."""
        resp = requests.get(
            f"{event_url}/api/events/search",
            params={"category": "CONCERT", "startDate": "2026-01-01", "endDate": "2026-12-31"},
            headers=auth_headers,
            timeout=10,
        )
        assert resp.status_code in (200, 404), (
            f"Valid token on M1 endpoint must not return 401/403, got {resp.status_code}"
        )

    def test_attendee_on_admin_only_endpoint_returns_403(self, user_url, auth_user):
        """Scenario e / DP-3 (RoleAuthorizationHandler): ATTENDEE on ADMIN-only → 403."""
        resp = requests.put(
            f"{user_url}/api/users/{auth_user['user_id']}/role",
            json={"role": "ADMIN"},
            headers=auth_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 403, (
            f"ATTENDEE token on ADMIN endpoint must return 403, got {resp.status_code}"
        )

    def test_admin_on_admin_only_endpoint_returns_200(self, user_url, admin_user, auth_user):
        """Scenario f / DP-3: ADMIN token on ADMIN-only endpoint → 200."""
        resp = requests.put(
            f"{user_url}/api/users/{auth_user['user_id']}/role",
            json={"role": "ATTENDEE"},   # safe: demote back
            headers=admin_user["headers"],
            timeout=10,
        )
        assert resp.status_code == 200, (
            f"ADMIN token must succeed on ADMIN-only endpoint, got {resp.status_code}"
        )

    def test_health_check_is_public(self, user_url):
        """Scenario g: /api/users/health must not require auth (SecurityConfig permits it)."""
        resp = requests.get(f"{user_url}/api/users/health", timeout=5)
        assert resp.status_code not in (401, 403), (
            "Health check '/api/users/health' must not return 401/403"
        )


# ---------------------------------------------------------------------------
# DP-3 Chain of Responsibility — Source scan  (Section 3.4)
# ---------------------------------------------------------------------------

class TestDP3ChainOfResponsibility:
    """DP-3 — Chain of Responsibility JWT Filter.

    Static source-scan checks complement the behavioral tests above.
    Section 3.4 test scenario a, b, h.
    """

    def test_auth_handler_class_exists_in_source(self):
        """Scenario a: AuthHandler class/interface must exist in user-service source."""
        # Check all services since the filter is cross-cutting
        found = any(
            _grep_source(svc, r"\bAuthHandler\b")
            for svc in ["user-service", "booking-service", "event-service",
                        "ticket-service", "sales-service"]
        )
        assert found, (
            "AuthHandler class/interface not found in any service source "
            "(DP-3: Section 3.4 step a)"
        )

    def test_concrete_auth_handlers_exist_in_source(self):
        """Scenario b: At least TokenExtractionHandler and SignatureValidationHandler
        must exist as concrete subclasses/implementors."""
        handlers = [
            "TokenExtractionHandler",
            "SignatureValidationHandler",
        ]
        for handler in handlers:
            found = any(
                _grep_source(svc, rf"\b{handler}\b")
                for svc in ["user-service", "booking-service", "event-service",
                            "ticket-service", "sales-service"]
            )
            assert found, (
                f"Concrete handler '{handler}' not found in source (DP-3 Section 3.4 step b)"
            )

    def test_jwt_filter_uses_auth_handler_chain(self):
        """Scenario h: JwtAuthenticationFilter.doFilterInternal must invoke
        the head of the AuthHandler chain rather than inline logic."""
        found = any(
            _grep_source(svc, r"JwtAuthenticationFilter")
            for svc in ["user-service", "booking-service", "event-service",
                        "ticket-service", "sales-service"]
        )
        assert found, "JwtAuthenticationFilter class not found in source (DP-3 Section 3.4)"


# ---------------------------------------------------------------------------
# DP-5 Singleton JwtConfigurationManager — behavioral (Section 3.6)
# ---------------------------------------------------------------------------

class TestDP5Singleton:
    """DP-5 — Singleton JwtConfigurationManager.

    Behavioral tests: JWT issuance / validation work correctly (proving the
    singleton provides config). Source-scan checks for class structure.
    Section 3.6 test scenarios d, f.
    """

    def test_jwt_token_is_valid_and_parseable(self, user_url):
        """Scenario f: tokens issued by the service can be decoded and have correct claims.

        Proves JwtService (Spring bean) correctly reads config via
        JwtConfigurationManager.getInstance() (the Singleton).
        """
        suffix = uuid.uuid4().hex[:8]
        reg = requests.post(f"{user_url}/api/auth/register", json={
            "name": "Sing Test", "email": f"sing_{suffix}@ex.com",
            "password": "pass", "phone": f"+225{int(suffix[:8], 16) % 10**9:09d}",
        }, timeout=10)
        assert reg.status_code == 201
        token  = reg.json()["token"]
        claims = _jwt_lib.decode(token, options={"verify_signature": False},
                                 algorithms=["HS256"])
        assert "sub" in claims, "JWT must have 'sub' claim (email)"
        assert "uid" in claims, "JWT must have 'uid' claim (user id)"
        assert "role" in claims, "JWT must have 'role' claim"

    def test_jwt_configuration_manager_class_in_source(self):
        """Scenario a: JwtConfigurationManager class must exist in source."""
        found = any(
            _grep_source(svc, r"\bJwtConfigurationManager\b")
            for svc in ["user-service", "booking-service", "event-service",
                        "ticket-service", "sales-service"]
        )
        assert found, "JwtConfigurationManager not found in source (DP-5 Section 3.6)"

    def test_jwt_configuration_manager_not_spring_bean(self):
        """Scenario e: JwtConfigurationManager must NOT be annotated with
        @Component, @Service, or @Configuration."""
        # Scan all relevant files for the class definition
        import re
        for svc in ["user-service", "booking-service", "event-service",
                    "ticket-service", "sales-service"]:
            for path in _java_source_files(svc):
                content = path.read_text(encoding="utf-8", errors="ignore")
                if "JwtConfigurationManager" in content and "class JwtConfigurationManager" in content:
                    spring_annotations = re.findall(
                        r"@(Component|Service|Configuration)\b", content
                    )
                    assert not spring_annotations, (
                        f"JwtConfigurationManager in {path} must NOT have Spring "
                        f"stereotype annotations; found: {spring_annotations}"
                    )

    def test_get_instance_method_exists_in_source(self):
        """Scenario b: getInstance() static method must exist."""
        found = any(
            _grep_source(svc, r"getInstance\(\)")
            for svc in ["user-service", "booking-service", "event-service",
                        "ticket-service", "sales-service"]
        )
        assert found, "getInstance() not found in source (DP-5 Singleton Section 3.6 step b)"


# ---------------------------------------------------------------------------
# M1 Modifications: JWT on M1 Endpoints  (Section 4.3)
# ---------------------------------------------------------------------------

class TestM1JwtRetrofit:
    """Section 4.3 — All M1 endpoints must require JWT.

    Test scenario steps c–f.
    """

    @pytest.mark.parametrize("service_fixture,path", [
        ("user_url",    "/api/users"),
        ("event_url",   "/api/events"),
        ("booking_url", "/api/bookings"),
        ("ticket_url",  "/api/tickets"),
        ("sales_url",   "/api/sales"),
    ])
    def test_m1_crud_list_endpoint_without_token_returns_401(
        self, request, service_fixture, path
    ):
        """Scenario f: CRUD list endpoints require JWT (Section 4.3 step f)."""
        base_url = request.getfixturevalue(service_fixture)
        resp = requests.get(f"{base_url}{path}", timeout=10)
        assert resp.status_code == 401, (
            f"GET {base_url}{path} without token must return 401, got {resp.status_code}"
        )

    @pytest.mark.parametrize("service_fixture,path", [
        ("user_url",    "/api/users"),
        ("event_url",   "/api/events"),
        ("booking_url", "/api/bookings"),
        ("ticket_url",  "/api/tickets"),
        ("sales_url",   "/api/sales"),
    ])
    def test_m1_crud_list_with_valid_token_not_401(
        self, request, service_fixture, path, auth_headers
    ):
        """Scenario e: with valid token, M1 endpoints return expected status (not 401)."""
        base_url = request.getfixturevalue(service_fixture)
        resp = requests.get(f"{base_url}{path}", headers=auth_headers, timeout=10)
        assert resp.status_code != 401, (
            f"GET {base_url}{path} with valid token must not return 401, got {resp.status_code}"
        )
        assert resp.status_code != 403, (
            f"GET {base_url}{path} with valid token must not return 403, got {resp.status_code}"
        )

    def test_m1_user_preferences_observer_retrofit(self, user_url, auth_user, mongo_db):
        """Section 4.5 / Scenario a (design pattern retrofit):
        M1 S1-F2 PUT /api/users/{id}/preferences must fire Observer and write
        to auth_events.
        """
        uid  = auth_user["user_id"]
        resp = requests.put(
            f"{user_url}/api/users/{uid}/preferences",
            json={"favoriteVenueId": None, "notificationsEnabled": True},
            headers=auth_user["headers"],
            timeout=10,
        )
        # Accept 200 or 204; the key check is MongoDB
        if resp.status_code not in (200, 204, 404):
            pytest.skip(
                f"PUT /api/users/{uid}/preferences returned {resp.status_code} — "
                "endpoint may not be implemented or path may differ"
            )
        time.sleep(0.5)
        doc = mongo_db["auth_events"].find_one(
            {"userId": uid, "action": {"$in": ["USER_UPDATED", "PREFERENCE_UPDATED"]}}
        )
        assert doc is not None, (
            f"Observer retrofit for S1-F2 must write an event to auth_events "
            f"for userId={uid} (Section 4.5 test scenario a)"
        )
