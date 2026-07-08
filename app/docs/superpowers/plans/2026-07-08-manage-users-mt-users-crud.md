# Manage Users (mt_users CRUD) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give HEAD (and ADMIN) full CRUD over the `mt_users` directory — a new `/mt-users` API on the FastAPI backend and a new "Manage Users" screen on the Android app, reachable from HeadHome.

**Architecture:** A dedicated `mt_users` vertical slice mirroring the existing MtMachine CRUD pattern. Backend: schemas + a `/mt-users` router gated with `require_role(user, {"HEAD"})` (which also admits ADMIN), registered in `main.py`; the `mt_users` table already exists so no migration is needed. Android: domain model → Retrofit API + DTOs → mock + repository impl → Hilt wiring → ViewModel + Compose screen/form → navigation, gated on `role ∈ {HEAD, ADMIN}`.

**Tech Stack:** Backend — FastAPI, SQLAlchemy 2.0, Pydantic 2, pytest + TestClient. Android — Kotlin, Jetpack Compose (Material3), Hilt, Retrofit, kotlinx.serialization.

**Two repos / two branches:**
- Backend repo: `D:\Maintenance module\backend` (git, currently on `feat/breakdown-timestamps-shutdown`). Backend commands run from this dir.
- Android repo: `D:\Maintenance module\FactoryOps` (git root; the module is `app/`, current branch `feat/role-based-reminders`). Gradle + git commands run from this dir. Source root: `app/src/main/java/com/candorfoods/factoryops/`.

---

## Part A — Backend (`D:\Maintenance module\backend`)

### Task 1: `/mt-users` CRUD router (TDD)

**Files:**
- Test: `tests/test_mt_users.py` (create)
- Modify: `app/schemas.py` (append 3 classes)
- Create: `app/api/mt_users.py`
- Modify: `app/main.py` (import + include_router)

- [ ] **Step 1: Write the failing tests**

Create `tests/test_mt_users.py`:

```python
"""CRUD for mt_users — HEAD/ADMIN only. New users have no password (shared login)."""

from app.models import MtUser

ENDPOINT = "/mt-users"


def _body(**over):
    b = {
        "emp_id": "E-101",
        "name": "Asha Kulkarni",
        "location": "A-185",
        "contact_no": "9876543210",
        "email_id": "asha@candorfoods.in",
        "role": "TECHNICIAN",
        "username": "AshaK",           # stored lowercased
    }
    b.update(over)
    return b


def test_create_persists_row_and_lowercases_username(login_as, db_session):
    c = login_as(role="HEAD", id=999)
    r = c.post(ENDPOINT, json=_body())
    assert r.status_code == 201, r.text
    body = r.json()
    assert body["username"] == "ashak"          # lowercased
    assert body["name"] == "Asha Kulkarni"
    assert body["emp_id"] == "E-101"
    assert body["role"] == "TECHNICIAN"
    assert isinstance(body["id"], int)

    row = db_session.query(MtUser).filter(MtUser.username == "ashak").one()
    assert row.contact_no == "9876543210"
    assert row.email_id == "asha@candorfoods.in"


def test_blank_optionals_become_null(login_as, db_session):
    c = login_as(role="HEAD", id=999)
    r = c.post(ENDPOINT, json=_body(username="nulls", emp_id="   ", contact_no=""))
    assert r.status_code == 201, r.text
    row = db_session.query(MtUser).filter(MtUser.username == "nulls").one()
    assert row.emp_id is None
    assert row.contact_no is None


def test_list_returns_created_users(login_as, db_session):
    c = login_as(role="HEAD", id=999)
    c.post(ENDPOINT, json=_body(username="one", name="One"))
    c.post(ENDPOINT, json=_body(username="two", name="Two"))
    r = c.get(ENDPOINT)
    assert r.status_code == 200, r.text
    names = [u["name"] for u in r.json()]
    assert "One" in names and "Two" in names


def test_create_missing_required_400(login_as, db_session):
    c = login_as(role="HEAD", id=999)
    assert c.post(ENDPOINT, json=_body(name="   ")).status_code == 400
    assert c.post(ENDPOINT, json=_body(username="  ")).status_code == 400


def test_create_duplicate_username_409(login_as, db_session):
    c = login_as(role="HEAD", id=999)
    assert c.post(ENDPOINT, json=_body(username="dupe")).status_code == 201
    assert c.post(ENDPOINT, json=_body(username="DUPE")).status_code == 409  # case-folded collision


def test_update_overwrites_fields(login_as, db_session):
    c = login_as(role="HEAD", id=999)
    created = c.post(ENDPOINT, json=_body(username="edit_me", name="Before")).json()
    uid = created["id"]
    r = c.put(f"{ENDPOINT}/{uid}", json=_body(username="edit_me", name="After", role="SUPERVISOR"))
    assert r.status_code == 200, r.text
    assert r.json()["name"] == "After"
    assert r.json()["role"] == "SUPERVISOR"


def test_update_missing_404(login_as, db_session):
    c = login_as(role="HEAD", id=999)
    assert c.put(f"{ENDPOINT}/123456", json=_body()).status_code == 404


def test_delete_removes_row(login_as, db_session):
    c = login_as(role="HEAD", id=999)
    uid = c.post(ENDPOINT, json=_body(username="gone")).json()["id"]
    assert c.delete(f"{ENDPOINT}/{uid}").status_code == 200
    assert db_session.query(MtUser).filter(MtUser.id == uid).first() is None


def test_delete_missing_404(login_as, db_session):
    c = login_as(role="HEAD", id=999)
    assert c.delete(f"{ENDPOINT}/123456").status_code == 404


def test_delete_self_400(login_as, db_session):
    # Seed a row whose id we authenticate as, then try to delete it.
    row = MtUser(name="Boss", username="boss", role="HEAD")
    db_session.add(row); db_session.commit(); db_session.refresh(row)
    c = login_as(role="HEAD", id=row.id)
    assert c.delete(f"{ENDPOINT}/{row.id}").status_code == 400


def test_manage_roles_allowed(login_as, db_session):
    for role in ("HEAD", "ADMIN"):
        c = login_as(role=role, id=999)
        assert c.post(ENDPOINT, json=_body(username=f"u_{role}")).status_code == 201


def test_non_manage_roles_forbidden_403(login_as, db_session):
    for role in ("OPERATOR", "TECHNICIAN", "SUPERVISOR"):
        c = login_as(role=role, id=999)
        assert c.post(ENDPOINT, json=_body()).status_code == 403
        assert c.get(ENDPOINT).status_code == 403


def test_requires_auth_401(client):
    assert client.post(ENDPOINT, json=_body()).status_code == 401
```

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `D:\Maintenance module\backend`): `python -m pytest tests/test_mt_users.py -v`
Expected: FAIL — every POST/GET/PUT/DELETE returns **404** (route not registered) or the schemas import fails. This confirms the endpoints don't exist yet.

- [ ] **Step 3: Add the schemas**

Append to `app/schemas.py` (after `MtMachineCreate`, near the other create/update DTOs). `Optional`, `BaseModel` are already imported at the top of the file:

```python
class MtUserDto(BaseModel):
    """A row from mt_users — the app-managed user directory (HEAD-only CRUD)."""
    id: int
    emp_id: Optional[str] = None
    name: str
    location: Optional[str] = None
    contact_no: Optional[str] = None
    email_id: Optional[str] = None
    role: Optional[str] = None
    username: str
    created_at: Optional[str] = None


class MtUserCreate(BaseModel):
    """Create a new mt_users row (HEAD/ADMIN only). Fields are Optional so a missing
    required one (name/username) surfaces as a clear 400 from the handler rather than a
    422 from validation. There is no password field — every user shares the fixed login
    password."""
    emp_id: Optional[str] = None
    name: Optional[str] = None
    location: Optional[str] = None
    contact_no: Optional[str] = None
    email_id: Optional[str] = None
    role: Optional[str] = None
    username: Optional[str] = None


class MtUserUpdate(BaseModel):
    """Full overwrite of an mt_users row (PUT /mt-users/{id}). Same shape as create; the
    app sends every editable field on each save."""
    emp_id: Optional[str] = None
    name: Optional[str] = None
    location: Optional[str] = None
    contact_no: Optional[str] = None
    email_id: Optional[str] = None
    role: Optional[str] = None
    username: Optional[str] = None
```

- [ ] **Step 4: Create the router**

Create `app/api/mt_users.py`:

```python
"""CRUD for mt_users — the app-managed user directory.

HEAD (and ADMIN, via require_role's superuser bypass) may list/create/update/delete
users. `username` is unique and stored lowercased (login looks it up lowercased). There
is no password column — new users log in with the shared fixed password."""

from datetime import datetime
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from ..database import get_rds
from ..models import MtUser
from ..schemas import MtUserDto, MtUserCreate, MtUserUpdate
from ..auth import get_current_user
from .pm_common import require_role

router = APIRouter(prefix="/mt-users", tags=["mt-users"])

# HEAD manages the user directory. ADMIN also passes (require_role superuser bypass).
_MANAGE_ROLES = {"HEAD"}


def _to_dto(u: MtUser) -> MtUserDto:
    return MtUserDto(
        id=u.id,
        emp_id=u.emp_id,
        name=u.name,
        location=u.location,
        contact_no=u.contact_no,
        email_id=u.email_id,
        role=u.role,
        username=u.username,
        created_at=u.created_at.isoformat() if u.created_at else None,
    )


def _clean(s: Optional[str]) -> Optional[str]:
    """Trim; blank -> None so optional columns stay null, not ''."""
    if s is None:
        return None
    s = s.strip()
    return s or None


@router.get("", response_model=List[MtUserDto])
def list_mt_users(
    db: Session = Depends(get_rds),
    user: MtUser = Depends(get_current_user),
):
    """All users, ordered by name. HEAD/ADMIN only."""
    require_role(user, _MANAGE_ROLES)
    rows = db.query(MtUser).order_by(MtUser.name.asc()).all()
    return [_to_dto(r) for r in rows]


@router.get("/{user_id}", response_model=MtUserDto)
def get_mt_user(
    user_id: int,
    db: Session = Depends(get_rds),
    user: MtUser = Depends(get_current_user),
):
    require_role(user, _MANAGE_ROLES)
    row = db.get(MtUser, user_id)
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    return _to_dto(row)


@router.post("", response_model=MtUserDto, status_code=status.HTTP_201_CREATED)
def create_mt_user(
    payload: MtUserCreate,
    db: Session = Depends(get_rds),
    user: MtUser = Depends(get_current_user),
):
    require_role(user, _MANAGE_ROLES)
    name = _clean(payload.name)
    username = (payload.username or "").strip().lower()
    if not name or not username:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="name and username are required",
        )
    row = MtUser(
        emp_id=_clean(payload.emp_id),
        name=name,
        location=_clean(payload.location),
        contact_no=_clean(payload.contact_no),
        email_id=_clean(payload.email_id),
        role=_clean(payload.role),
        username=username,
        created_at=datetime.utcnow(),
    )
    db.add(row)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="username already exists",
        )
    db.refresh(row)
    return _to_dto(row)


@router.put("/{user_id}", response_model=MtUserDto)
def update_mt_user(
    user_id: int,
    payload: MtUserUpdate,
    db: Session = Depends(get_rds),
    user: MtUser = Depends(get_current_user),
):
    require_role(user, _MANAGE_ROLES)
    row = db.get(MtUser, user_id)
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    name = _clean(payload.name)
    username = (payload.username or "").strip().lower()
    if not name or not username:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="name and username are required",
        )
    row.emp_id = _clean(payload.emp_id)
    row.name = name
    row.location = _clean(payload.location)
    row.contact_no = _clean(payload.contact_no)
    row.email_id = _clean(payload.email_id)
    row.role = _clean(payload.role)
    row.username = username
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="username already exists",
        )
    db.refresh(row)
    return _to_dto(row)


@router.delete("/{user_id}", response_model=MtUserDto)
def delete_mt_user(
    user_id: int,
    db: Session = Depends(get_rds),
    user: MtUser = Depends(get_current_user),
):
    require_role(user, _MANAGE_ROLES)
    if user_id == user.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You cannot delete your own account",
        )
    row = db.get(MtUser, user_id)
    if row is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")
    dto = _to_dto(row)
    db.delete(row)
    db.commit()
    return dto
```

- [ ] **Step 5: Register the router in `app/main.py`**

Add `mt_users` to the `from .api import (...)` block (append to the last line of the tuple):

```python
from .api import (
    auth, machines, energy, breakdowns, floors, mt_machines,
    preventive_maintenance, machine_transfers, breakdown_records,
    live, reports, head, floor_readings, asset_schedules, devices,
    users, pm_plans, pm_work_orders, mt_users,
)
```

And add this line inside `create_app()` alongside the other `include_router` calls (e.g. right after `app.include_router(users.router)`):

```python
    app.include_router(mt_users.router)
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `python -m pytest tests/test_mt_users.py -v`
Expected: PASS — all 12 tests green.

- [ ] **Step 7: Commit (backend repo)**

```bash
cd "D:/Maintenance module/backend"
git add app/schemas.py app/api/mt_users.py app/main.py tests/test_mt_users.py
git commit -m "feat: HEAD-only CRUD for mt_users (/mt-users)"
```

---

## Part B — Android (`D:\Maintenance module\FactoryOps`, module `app/`)

All source paths below are under `app/src/main/java/com/candorfoods/factoryops/`.
Verification commands run from `D:\Maintenance module\FactoryOps`:
- Intermediate compile: `.\gradlew.bat :app:compileDebugKotlin`
- Full build (validates the Hilt graph): `.\gradlew.bat :app:assembleDebug`

### Task 2: Domain model + repository interface

**Files:**
- Create: `domain/model/MtUser.kt`
- Create: `domain/repository/MtUserRepository.kt`

- [ ] **Step 1: Create the domain model**

`domain/model/MtUser.kt`:

```kotlin
package com.candorfoods.factoryops.domain.model

/**
 * A row from the `mt_users` directory (HEAD-only CRUD). `id` is 0 for a not-yet-saved
 * user; the backend assigns it. `role` is a free-text column but the UI constrains it to
 * the [UserRole] names. There is no password — new users log in with the shared fixed
 * password.
 */
data class MtUser(
    val id: Int = 0,
    val empId: String? = null,
    val name: String,
    val location: String? = null,
    val contactNo: String? = null,
    val emailId: String? = null,
    val role: String? = null,
    val username: String,
)
```

- [ ] **Step 2: Create the repository interface**

`domain/repository/MtUserRepository.kt`:

```kotlin
package com.candorfoods.factoryops.domain.repository

import com.candorfoods.factoryops.domain.model.MtUser
import com.candorfoods.factoryops.domain.model.Result

interface MtUserRepository {
    /** All users in the directory (HEAD/ADMIN only) → GET /mt-users. */
    suspend fun list(): Result<List<MtUser>>

    /** Create a new user → POST /mt-users. */
    suspend fun add(user: MtUser): Result<MtUser>

    /** Full overwrite of an existing user → PUT /mt-users/{id}. */
    suspend fun update(user: MtUser): Result<MtUser>

    /** Delete a user → DELETE /mt-users/{id}. */
    suspend fun delete(id: Int): Result<Unit>
}
```

- [ ] **Step 3: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (new pure-Kotlin files, no wiring yet).

### Task 3: DTOs + Retrofit API

**Files:**
- Create: `data/remote/dto/MtUserDto.kt`
- Create: `data/remote/api/MtUserApi.kt`

- [ ] **Step 1: Create the DTOs + mappers**

`data/remote/dto/MtUserDto.kt`:

```kotlin
package com.candorfoods.factoryops.data.remote.dto

import com.candorfoods.factoryops.domain.model.MtUser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors an mt_users row served by GET /mt-users. ignoreUnknownKeys handles created_at. */
@Serializable
data class MtUserDto(
    @SerialName("id")         val id: Int = 0,
    @SerialName("emp_id")     val empId: String? = null,
    @SerialName("name")       val name: String = "",
    @SerialName("location")   val location: String? = null,
    @SerialName("contact_no") val contactNo: String? = null,
    @SerialName("email_id")   val emailId: String? = null,
    @SerialName("role")       val role: String? = null,
    @SerialName("username")   val username: String = "",
)

fun MtUserDto.toDomain(): MtUser = MtUser(
    id = id,
    empId = empId,
    name = name,
    location = location,
    contactNo = contactNo,
    emailId = emailId,
    role = role,
    username = username,
)

/**
 * Body for POST /mt-users and PUT /mt-users/{id} (same full-row shape for both). `id` is
 * not sent — it's assigned on create and is the path param on update.
 */
@Serializable
data class MtUserWriteDto(
    @SerialName("emp_id")     val empId: String? = null,
    @SerialName("name")       val name: String,
    @SerialName("location")   val location: String? = null,
    @SerialName("contact_no") val contactNo: String? = null,
    @SerialName("email_id")   val emailId: String? = null,
    @SerialName("role")       val role: String? = null,
    @SerialName("username")   val username: String,
)

fun MtUser.toWriteDto(): MtUserWriteDto = MtUserWriteDto(
    empId = empId,
    name = name,
    location = location,
    contactNo = contactNo,
    emailId = emailId,
    role = role,
    username = username,
)
```

- [ ] **Step 2: Create the Retrofit API interface**

`data/remote/api/MtUserApi.kt`:

```kotlin
package com.candorfoods.factoryops.data.remote.api

import com.candorfoods.factoryops.data.remote.dto.MtUserDto
import com.candorfoods.factoryops.data.remote.dto.MtUserWriteDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/** HEAD-only user directory. Auth is passed per-call as a "Bearer <token>" header. */
interface MtUserApi {
    @GET("mt-users")
    suspend fun list(@Header("Authorization") token: String): List<MtUserDto>

    @POST("mt-users")
    suspend fun add(
        @Header("Authorization") token: String,
        @Body dto: MtUserWriteDto,
    ): MtUserDto

    @PUT("mt-users/{id}")
    suspend fun update(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
        @Body dto: MtUserWriteDto,
    ): MtUserDto

    @DELETE("mt-users/{id}")
    suspend fun delete(
        @Header("Authorization") token: String,
        @Path("id") id: Int,
    ): MtUserDto
}
```

- [ ] **Step 3: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

### Task 4: Mock API + repository impl + Hilt wiring

**Files:**
- Create: `data/remote/mock/MockMtUserApi.kt`
- Create: `data/repository/MtUserRepositoryImpl.kt`
- Modify: `di/NetworkModule.kt` (import + `provideMtUserApi`)
- Modify: `di/RepositoryModule.kt` (imports + `bindMtUserRepository`)

- [ ] **Step 1: Create the mock API**

`data/remote/mock/MockMtUserApi.kt`:

```kotlin
package com.candorfoods.factoryops.data.remote.mock

import com.candorfoods.factoryops.data.remote.api.MtUserApi
import com.candorfoods.factoryops.data.remote.dto.MtUserDto
import com.candorfoods.factoryops.data.remote.dto.MtUserWriteDto
import kotlinx.coroutines.delay

/** In-memory mt_users for USE_MOCK_API builds. Mirrors the backend's lowercase-username
 *  and duplicate-username behaviour so the Manage Users flow can be exercised offline. */
class MockMtUserApi : MtUserApi {

    private val users = mutableListOf(
        MtUserDto(1, "E-001", "Shubham Lohar", "both", "9000000001", "shubham@candorfoods.in", "HEAD", "shubhamlohar"),
        MtUserDto(2, "E-002", "Abhishek Ghorpade", "both", "9000000002", null, "HEAD", "abhishekghorpade"),
        MtUserDto(3, "E-003", "Ravi Patil", "A-185", "9000000003", null, "TECHNICIAN", "ravipatil"),
    )
    private var nextId = 4

    override suspend fun list(token: String): List<MtUserDto> {
        delay(300)
        return users.sortedBy { it.name.lowercase() }
    }

    override suspend fun add(token: String, dto: MtUserWriteDto): MtUserDto {
        delay(300)
        val uname = dto.username.trim().lowercase()
        if (users.any { it.username == uname }) error("username already exists")
        val created = MtUserDto(
            id = nextId++,
            empId = dto.empId, name = dto.name, location = dto.location,
            contactNo = dto.contactNo, emailId = dto.emailId, role = dto.role,
            username = uname,
        )
        users.add(created)
        return created
    }

    override suspend fun update(token: String, id: Int, dto: MtUserWriteDto): MtUserDto {
        delay(300)
        val uname = dto.username.trim().lowercase()
        if (users.any { it.username == uname && it.id != id }) error("username already exists")
        val idx = users.indexOfFirst { it.id == id }
        if (idx < 0) error("User not found")
        val updated = MtUserDto(
            id = id,
            empId = dto.empId, name = dto.name, location = dto.location,
            contactNo = dto.contactNo, emailId = dto.emailId, role = dto.role,
            username = uname,
        )
        users[idx] = updated
        return updated
    }

    override suspend fun delete(token: String, id: Int): MtUserDto {
        delay(300)
        val idx = users.indexOfFirst { it.id == id }
        if (idx < 0) error("User not found")
        return users.removeAt(idx)
    }
}
```

- [ ] **Step 2: Create the repository impl**

`data/repository/MtUserRepositoryImpl.kt`:

```kotlin
package com.candorfoods.factoryops.data.repository

import com.candorfoods.factoryops.data.local.AuthPreferences
import com.candorfoods.factoryops.data.remote.api.MtUserApi
import com.candorfoods.factoryops.data.remote.dto.toDomain
import com.candorfoods.factoryops.data.remote.dto.toWriteDto
import com.candorfoods.factoryops.domain.model.MtUser
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.repository.MtUserRepository
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MtUserRepositoryImpl @Inject constructor(
    private val mtUserApi: MtUserApi,
    private val authPreferences: AuthPreferences,
) : MtUserRepository {

    override suspend fun list(): Result<List<MtUser>> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            Result.Success(mtUserApi.list("Bearer $token").map { it.toDomain() })
        } catch (e: Exception) {
            Result.Error(friendly(e, "Failed to load users"), e)
        }
    }

    override suspend fun add(user: MtUser): Result<MtUser> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            Result.Success(mtUserApi.add("Bearer $token", user.toWriteDto()).toDomain())
        } catch (e: Exception) {
            Result.Error(friendly(e, "Failed to add user"), e)
        }
    }

    override suspend fun update(user: MtUser): Result<MtUser> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            Result.Success(mtUserApi.update("Bearer $token", user.id, user.toWriteDto()).toDomain())
        } catch (e: Exception) {
            Result.Error(friendly(e, "Failed to update user"), e)
        }
    }

    override suspend fun delete(id: Int): Result<Unit> {
        val token = authPreferences.getToken() ?: return Result.Error("Not logged in")
        return try {
            mtUserApi.delete("Bearer $token", id)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(friendly(e, "Failed to delete user"), e)
        }
    }

    /** Map common backend errors to user-facing text; fall back to the raw message. */
    private fun friendly(e: Exception, fallback: String): String = when {
        e is HttpException && e.code() == 409 -> "That username is already taken"
        e is HttpException && e.code() == 403 -> "You don't have permission to do that"
        e is HttpException && e.code() == 404 -> "User not found"
        e is HttpException && e.code() == 400 -> "Please fill in name and username"
        else -> e.message ?: fallback
    }
}
```

- [ ] **Step 3: Provide the API in `di/NetworkModule.kt`**

Add these imports alongside the other api/mock imports:

```kotlin
import com.candorfoods.factoryops.data.remote.api.MtUserApi
import com.candorfoods.factoryops.data.remote.mock.MockMtUserApi
```

Add this provider next to `provideMtMachineApi`:

```kotlin
    @Provides
    @Singleton
    fun provideMtUserApi(retrofit: Retrofit): MtUserApi =
        if (BuildConfig.USE_MOCK_API) MockMtUserApi()
        else retrofit.create(MtUserApi::class.java)
```

- [ ] **Step 4: Bind the repository in `di/RepositoryModule.kt`**

Add these imports alongside the other repository imports:

```kotlin
import com.candorfoods.factoryops.data.repository.MtUserRepositoryImpl
import com.candorfoods.factoryops.domain.repository.MtUserRepository
```

Add this binding next to `bindMtMachineRepository`:

```kotlin
    @Binds @Singleton
    abstract fun bindMtUserRepository(impl: MtUserRepositoryImpl): MtUserRepository
```

- [ ] **Step 5: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

### Task 5: ViewModel

**Files:**
- Create: `presentation/user/ManageUsersViewModel.kt`

- [ ] **Step 1: Create the ViewModel + UI state**

`presentation/user/ManageUsersViewModel.kt`:

```kotlin
package com.candorfoods.factoryops.presentation.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.candorfoods.factoryops.domain.model.MtUser
import com.candorfoods.factoryops.domain.model.Result
import com.candorfoods.factoryops.domain.model.User
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.domain.repository.MtUserRepository
import com.candorfoods.factoryops.domain.usecase.auth.GetCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageUsersUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentUser: User? = null,
    val users: List<MtUser> = emptyList(),
    val editing: MtUser? = null,        // set while editing; null = adding
    val showSheet: Boolean = false,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val pendingDelete: MtUser? = null,  // non-null while the confirm dialog is open
    val isDeleting: Boolean = false,
) {
    /** HEAD manages the directory; ADMIN also allowed (matches the backend). */
    val canManage: Boolean
        get() = currentUser?.role == UserRole.HEAD || currentUser?.role == UserRole.ADMIN
}

@HiltViewModel
class ManageUsersViewModel @Inject constructor(
    private val getCurrentUser: GetCurrentUserUseCase,
    private val mtUserRepository: MtUserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageUsersUiState())
    val uiState: StateFlow<ManageUsersUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getCurrentUser().collect { user -> _uiState.update { it.copy(currentUser = user) } }
        }
        load(initial = true)
    }

    fun onAddClicked() = _uiState.update { it.copy(showSheet = true, editing = null, saveError = null) }
    fun onEditClicked(user: MtUser) = _uiState.update { it.copy(showSheet = true, editing = user, saveError = null) }
    fun onSheetDismiss() = _uiState.update { it.copy(showSheet = false, editing = null, saveError = null) }

    fun onSubmit(user: MtUser) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null) }
            val editingId = _uiState.value.editing?.id
            val result = if (editingId != null && editingId != 0)
                mtUserRepository.update(user.copy(id = editingId))
            else
                mtUserRepository.add(user)
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false, showSheet = false, editing = null) }
                    load(initial = false)
                }
                is Result.Error -> _uiState.update { it.copy(isSaving = false, saveError = result.message) }
                is Result.Loading -> _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onDeleteClicked(user: MtUser) = _uiState.update { it.copy(pendingDelete = user) }
    fun onDeleteDismiss() = _uiState.update { it.copy(pendingDelete = null) }

    fun onConfirmDelete() {
        val target = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            when (val r = mtUserRepository.delete(target.id)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isDeleting = false, pendingDelete = null) }
                    load(initial = false)
                }
                is Result.Error ->
                    _uiState.update { it.copy(isDeleting = false, pendingDelete = null, error = r.message) }
                is Result.Loading -> _uiState.update { it.copy(isDeleting = false) }
            }
        }
    }

    fun onRefresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = initial, error = null) }
            when (val r = mtUserRepository.list()) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, users = r.data) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = r.message) }
                is Result.Loading -> Unit
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

### Task 6: Add/edit form sheet

**Files:**
- Create: `presentation/user/UserFormSheet.kt`

- [ ] **Step 1: Create the form sheet**

`presentation/user/UserFormSheet.kt`:

```kotlin
package com.candorfoods.factoryops.presentation.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.candorfoods.factoryops.domain.model.MtUser
import com.candorfoods.factoryops.domain.model.UserRole
import com.candorfoods.factoryops.presentation.common.components.PrimaryButton
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonSize
import com.candorfoods.factoryops.presentation.common.components.PrimaryButtonStyle

/**
 * Add/edit sheet over the mt_users columns. When [existing] is null it's an add form;
 * otherwise the fields pre-fill for an edit. Builds an MtUser and hands it to [onSubmit]
 * (the ViewModel decides POST vs PUT). No password field — new users share the fixed login.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFormSheet(
    existing: MtUser?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (MtUser) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var empId by remember { mutableStateOf(existing?.empId ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var contactNo by remember { mutableStateOf(existing?.contactNo ?: "") }
    var emailId by remember { mutableStateOf(existing?.emailId ?: "") }
    var role by remember { mutableStateOf(existing?.role?.uppercase() ?: UserRole.OPERATOR.name) }

    val emailValid = emailId.isBlank() ||
        Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(emailId.trim())
    val canSubmit = name.isNotBlank() && username.isNotBlank() && emailValid && !isSaving

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (existing == null) "Add new user" else "Edit user",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Field("Name *", name) { name = it }
            Field("Username *", username) { username = it.trim() }
            Text(
                "Username is the login id (stored in lowercase). New users sign in with the shared password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            RoleDropdown(role) { role = it }
            Field("Employee ID", empId) { empId = it }
            Field("Location", location) { location = it }
            Field("Contact no.", contactNo, keyboardType = KeyboardType.Phone) { contactNo = it }
            Field("Email", emailId, keyboardType = KeyboardType.Email) { emailId = it }
            if (!emailValid) {
                Text(
                    "Enter a valid email or leave it blank.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(4.dp))

            PrimaryButton(
                text = if (existing == null) "Add user" else "Save changes",
                onClick = {
                    onSubmit(
                        MtUser(
                            id = existing?.id ?: 0,
                            name = name.trim(),
                            username = username.trim().lowercase(),
                            empId = empId.trim().ifBlank { null },
                            location = location.trim().ifBlank { null },
                            contactNo = contactNo.trim().ifBlank { null },
                            emailId = emailId.trim().ifBlank { null },
                            role = role,
                        )
                    )
                },
                enabled = canSubmit,
                loading = isSaving,
                style = PrimaryButtonStyle.Primary,
                size = PrimaryButtonSize.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), enabled = !isSaving) {
                Text("Cancel")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleDropdown(value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Role *") },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UserRole.values().forEach { r ->
                DropdownMenuItem(text = { Text(r.name) }, onClick = { onSelect(r.name); expanded = false })
            }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

### Task 7: Manage Users screen

**Files:**
- Create: `presentation/user/ManageUsersScreen.kt`

- [ ] **Step 1: Create the screen**

`presentation/user/ManageUsersScreen.kt`:

```kotlin
package com.candorfoods.factoryops.presentation.user

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.candorfoods.factoryops.domain.model.MtUser
import com.candorfoods.factoryops.presentation.common.components.FactoryCard
import com.candorfoods.factoryops.presentation.common.theme.Spacing

/** HEAD/ADMIN user directory: list + add + edit + delete over /mt-users. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageUsersScreen(
    onBack: () -> Unit,
    viewModel: ManageUsersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Users") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (uiState.canManage) {
                FloatingActionButton(onClick = viewModel::onAddClicked) {
                    Icon(Icons.Default.Add, contentDescription = "Add new user")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !uiState.canManage && !uiState.isLoading ->
                    Centered("Only Head can manage users.")
                uiState.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                uiState.error != null ->
                    Centered(uiState.error!!)
                uiState.users.isEmpty() ->
                    Centered("No users yet. Tap + to add one.")
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.base),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(uiState.users, key = { it.id }) { user ->
                        UserRow(
                            user = user,
                            onEdit = { viewModel.onEditClicked(user) },
                            onDelete = { viewModel.onDeleteClicked(user) },
                        )
                    }
                }
            }
        }
    }

    if (uiState.showSheet) {
        UserFormSheet(
            existing = uiState.editing,
            isSaving = uiState.isSaving,
            error = uiState.saveError,
            onDismiss = viewModel::onSheetDismiss,
            onSubmit = viewModel::onSubmit,
        )
    }

    uiState.pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::onDeleteDismiss,
            title = { Text("Delete user?") },
            text = { Text("Remove ${target.name} (${target.username})? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDelete, enabled = !uiState.isDeleting) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDeleteDismiss) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BoxScope.Centered(text: String) {
    Text(
        text,
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
}

@Composable
private fun UserRow(user: MtUser, onEdit: () -> Unit, onDelete: () -> Unit) {
    FactoryCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    buildString {
                        append(user.username)
                        user.role?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                        user.location?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

### Task 8: Navigation wiring + final build + commit

**Files:**
- Modify: `navigation/Screen.kt` (add `ManageUsers` route)
- Modify: `navigation/NavGraph.kt` (import + `composable` + HeadHome lambda)
- Modify: `presentation/head/HeadHomeScreen.kt` (new `onManageUsers` param + tile)

- [ ] **Step 1: Add the route to `navigation/Screen.kt`**

In the "Head oversight" block (right after `object HeadChecklistRecords ...`), add:

```kotlin
    object ManageUsers          : Screen("manage_users")           // head/admin user directory
```

- [ ] **Step 2: Add the `onManageUsers` param + tile to `presentation/head/HeadHomeScreen.kt`**

Add this import next to the other `material.icons.filled` imports:

```kotlin
import androidx.compose.material.icons.filled.Person
```

Add a new lambda parameter to the `HeadHomeScreen` signature (after `onSettings: () -> Unit,`):

```kotlin
    onManageUsers: () -> Unit,
```

Add an "Administration" group with the Manage Users tile at the end of the `LazyColumn` (after the Analytics group's `item { SectionCard(... "Reports" ...) }`):

```kotlin
            item { GroupHeader("Administration") }
            item { SectionCard(Icons.Filled.Person, "Manage Users", "Add, edit or remove app users", onClick = onManageUsers) }
```

- [ ] **Step 3: Wire it in `navigation/NavGraph.kt`**

Add this import next to the other `presentation.*` imports:

```kotlin
import com.candorfoods.factoryops.presentation.user.ManageUsersScreen
```

Pass the new lambda in the existing `HeadHomeScreen(...)` call (inside `composable(Screen.HeadHome.route) { ... }`), after `onSettings = ...,`:

```kotlin
                onManageUsers     = { navController.navigate(Screen.ManageUsers.route) },
```

Register the screen — add this `composable` right after the `composable(Screen.HeadChecklistRecords.route) { ... }` block:

```kotlin
        composable(Screen.ManageUsers.route) {
            ManageUsersScreen(onBack = { navController.popBackStack() })
        }
```

- [ ] **Step 4: Full build to validate the Hilt graph**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL — confirms the new `MtUserApi` provider, `MtUserRepository` binding, and `ManageUsersViewModel` injection all resolve.

- [ ] **Step 5: Manual verification against the mock**

With defaults (`USE_MOCK_API=true`, `USE_MOCK_AUTH=true` in `local.properties`), install & run the debug app, log in as a HEAD user, and confirm:
1. HeadHome shows an **Administration → Manage Users** tile.
2. Tapping it opens the list (3 seeded users).
3. **+** opens the add sheet; adding a user (unique username) makes it appear in the list.
4. Adding a user with an existing username shows "That username is already taken" (mock: "username already exists").
5. Tapping a row / its edit icon pre-fills the sheet; saving updates the row.
6. The delete icon opens the confirm dialog; confirming removes the row.

Run: `.\gradlew.bat :app:installDebug` (device/emulator connected), then exercise the flow above.
Expected: all six behaviours as described.

- [ ] **Step 6: Commit (Android repo)**

```bash
cd "D:/Maintenance module/FactoryOps"
# Review the changed/new files first, then stage them:
git status
git add app/src/main/java/com/candorfoods/factoryops/domain/model/MtUser.kt \
        app/src/main/java/com/candorfoods/factoryops/domain/repository/MtUserRepository.kt \
        app/src/main/java/com/candorfoods/factoryops/data/remote/dto/MtUserDto.kt \
        app/src/main/java/com/candorfoods/factoryops/data/remote/api/MtUserApi.kt \
        app/src/main/java/com/candorfoods/factoryops/data/remote/mock/MockMtUserApi.kt \
        app/src/main/java/com/candorfoods/factoryops/data/repository/MtUserRepositoryImpl.kt \
        app/src/main/java/com/candorfoods/factoryops/di/NetworkModule.kt \
        app/src/main/java/com/candorfoods/factoryops/di/RepositoryModule.kt \
        app/src/main/java/com/candorfoods/factoryops/presentation/user/ \
        app/src/main/java/com/candorfoods/factoryops/navigation/Screen.kt \
        app/src/main/java/com/candorfoods/factoryops/navigation/NavGraph.kt \
        app/src/main/java/com/candorfoods/factoryops/presentation/head/HeadHomeScreen.kt
git commit -m "feat: HEAD-only Manage Users screen (mt_users CRUD)"
```

---

## Notes / conventions honored

- **No migration:** the `mt_users` table already exists (`app/models.py`), auto-created at startup; adding the router is sufficient.
- **No password:** the fixed shared password is unchanged; the create/edit forms never touch a password.
- **Username normalization:** stored `strip().lower()` on both sides so login (which looks up lowercased) always matches.
- **Access:** `require_role(user, {"HEAD"})` admits HEAD and ADMIN (per the design decision); the Android tile lives on HeadHome (HEAD path) and the screen also guards on `canManage`.
- **Self-delete guard:** the backend refuses to delete the caller's own row (400); the Android UI surfaces that error.
- **TDD** on the backend (pytest). The Android module has no unit-test harness (the project verifies UI features manually against the mock), so Android tasks use compile + a final `assembleDebug` + a manual mock walkthrough as verification, matching existing practice.
```