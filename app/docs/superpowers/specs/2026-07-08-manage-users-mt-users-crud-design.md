# Manage Users (mt_users CRUD) — Design

**Date:** 2026-07-08
**Status:** Approved (design)
**Repos touched:** `FactoryOps/app` (Android) and `D:\Maintenance module\backend` (FastAPI)

## 1. Summary

Add a HEAD-managed **"Add New User" / Manage Users** feature that performs full CRUD on the
existing `mt_users` table. Access is restricted to users whose role is `HEAD` (with `ADMIN`
also permitted, via the existing `require_role` superuser bypass).

The `mt_users` table already exists in the RDS Postgres database and is read today only by
login. This work adds create/read/update/delete endpoints on the backend and a new Android
screen to drive them. **No database migration is required** — the table and columns already exist.

### Table columns (`mt_users`)

| Column | Type | Required | Notes |
|---|---|---|---|
| `id` | int PK | auto | server-assigned |
| `emp_id` | str(20) | optional | |
| `name` | str(255) | **required** | |
| `location` | str(100) | optional | free-text; maps to `plant_id` at login |
| `contact_no` | str(15) | optional | |
| `email_id` | str(255) | optional | |
| `role` | str(50) | optional | free-text in DB; app picks from a fixed dropdown |
| `username` | str, unique | **required** | login key; stored lowercased |
| `created_at` | datetime | auto | |

There is **no password column** — every user authenticates with a shared fixed password
(`pass123`). So "add user" simply inserts a row; the new person logs in with that shared
password plus their `username`. No password field appears anywhere in this feature.

## 2. Goals / Non-goals

**Goals**
- HEAD (or ADMIN) can list, add, edit, and delete users in `mt_users` from the app.
- Backend exposes a dedicated, role-gated `/mt-users` CRUD API.
- Mirror existing project conventions (MtMachine slice on Android; `mt_machines`/`pm_plans`
  routers on the backend) so the code reads like its neighbors.

**Non-goals (YAGNI)**
- No password management (shared fixed password remains).
- No plant/location dropdown (free-text for now).
- No separate ADMIN home screen / navigation entry (ADMIN is allowed at the API + screen
  level, but the in-app entry point lives on HeadHome).
- No soft-delete / audit history (hard delete).
- No bulk import/export.

## 3. Approach

A **new dedicated `mt-users` vertical slice on both sides**, mirroring the MtMachine CRUD
pattern. Rejected alternatives:

- **Extend the existing `/users` roster endpoint** — it is read-only, plant-scoped, and used
  elsewhere (PM assignment dropdowns). Adding writes there muddies its purpose and risks its
  scoping semantics.
- **Generic admin table editor** — overkill / YAGNI.

## 4. Backend design — `D:\Maintenance module\backend`

### 4.1 Schemas (`app/schemas.py`)

Follow the house convention: request bodies use Optional fields with `None` defaults, and the
handler validates business-required fields to return a clean `400` (not a Pydantic `422`).

- `MtUserDto` (response): `id, emp_id, name, location, contact_no, email_id, role, username, created_at`
  (`created_at` serialized as ISO string or null).
- `MtUserCreate`: `name, emp_id, location, contact_no, email_id, role, username` — all Optional
  in the schema; handler requires `name` and `username`.
- `MtUserUpdate`: same shape as create (full-overwrite PUT, matching the app's "send every
  editable field on each save" convention).

### 4.2 Router (`app/api/mt_users.py`)

`router = APIRouter(prefix="/mt-users", tags=["mt-users"])`. A `_to_dto(user: MtUser) -> MtUserDto`
mapper. Every handler depends on `db = Depends(get_rds)` and `user = Depends(get_current_user)`.
Writes and reads are gated with `require_role(user, {"HEAD"})` (admits HEAD and ADMIN).

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/mt-users` | list all users ordered by `name`; returns `list[MtUserDto]` |
| `GET` | `/mt-users/{user_id}` | one user; `404` if missing |
| `POST` | `/mt-users` | validate `name`+`username` → else `400`; normalize `username = strip().lower()`; insert; `IntegrityError` → rollback → `409 "username already exists"`; return `201` + `MtUserDto` |
| `PUT` | `/mt-users/{user_id}` | `404` if missing; validate required fields; full overwrite; `username` normalized; duplicate → `409` |
| `DELETE` | `/mt-users/{user_id}` | `404` if missing; **guard: if `user_id == current user.id` → `400 "cannot delete your own account"`**; hard delete; return the deleted `MtUserDto` |

Notes:
- `username` is normalized to `strip().lower()` on create/update because login looks users up
  with `username.strip().lower()`.
- `role` is stored as sent (free-text column); the app constrains the input to a known set.

### 4.3 Registration (`app/main.py`)

Add `mt_users` to the `from .api import (...)` block and `app.include_router(mt_users.router)`
in `create_app()`.

### 4.4 Tests (`tests/test_mt_users.py`)

Using the existing `login_as(role=..., location=...)` harness (`MtUser` is already registered
in `tests/conftest.py` `_TABLES`):
- create → 201 and appears in list.
- create with missing `name`/`username` → 400.
- create duplicate `username` → 409.
- update → 200 reflects changes; update missing id → 404.
- delete → 200; delete missing id → 404; **self-delete → 400**.
- `OPERATOR`/`TECHNICIAN` on any write → 403; HEAD and ADMIN allowed.

## 5. Android design — `FactoryOps/app`

Package root: `com.candorfoods.factoryops`. Mirror the MtMachine vertical slice.

### 5.1 Domain
- `domain/model/MtUser.kt` — `data class MtUser(id, empId, name, location, contactNo, emailId, role, username)`.
  `role` kept as `String` (backend column is free-text); the form supplies enum-name values.
- `domain/repository/MtUserRepository.kt` — suspend functions returning `Result<…>`:
  `list(): Result<List<MtUser>>`, `add(user): Result<MtUser>`, `update(user): Result<MtUser>`,
  `delete(id): Result<Unit>`.

### 5.2 Data
- `data/remote/api/MtUserApi.kt` — Retrofit interface with per-call
  `@Header("Authorization") token: String` ("Bearer …"):
  `@GET("mt-users")`, `@POST("mt-users")`, `@PUT("mt-users/{id}")`, `@DELETE("mt-users/{id}")`.
- `data/remote/dto/MtUserDto.kt` — `@Serializable` DTOs with `@SerialName` snake_case:
  `MtUserDto`, `MtUserCreateDto`, `MtUserUpdateDto`, plus `toDomain()`, `toCreateDto()`,
  `toUpdateDto()` extension mappers.
- `data/remote/mock/MockMtUserApi.kt` — `class MockMtUserApi : MtUserApi` with an in-memory
  seeded list and `delay(...)`, implementing the same suspend functions (assigns fake ids,
  enforces duplicate-username behavior loosely).
- `data/repository/MtUserRepositoryImpl.kt` — `@Singleton @Inject`; reads token from
  `AuthPreferences`; wraps each call in try/catch → `Result.Success/Error`.

### 5.3 DI
- `di/NetworkModule.kt` — `provideMtUserApi(retrofit)` returning `MockMtUserApi()` when
  `BuildConfig.USE_MOCK_API`, else `retrofit.create(MtUserApi::class.java)`.
- `di/RepositoryModule.kt` — `@Binds bindMtUserRepository(impl): MtUserRepository`.

### 5.4 Presentation (`presentation/user/`)
- `ManageUsersViewModel.kt` — `@HiltViewModel`; collects `GetCurrentUserUseCase` to derive
  `canManage = role ∈ {HEAD, ADMIN}`; loads the user list; holds UI state for the shared
  add/edit sheet and the delete-confirmation dialog; handlers `onAddUser`, `onEditUser`,
  `onSubmitUser` (create or update), `onDeleteUser`, `onConfirmDelete`, `refresh`.
- `ManageUsersScreen.kt` — `@Composable(... viewModel = hiltViewModel())`; `Scaffold` with:
  - a list of user rows (name, role, username; each row exposes edit + delete),
  - a **role-gated FAB** ("Add New User") shown when `canManage`,
  - the shared add/edit `ModalBottomSheet`,
  - a delete-confirmation `AlertDialog`,
  - loading / error / empty and `!canManage` denied states.
- `UserFormSheet.kt` — `ModalBottomSheet` form reused for add and edit: `OutlinedTextField`s
  for `name*`, `username*`, `empId`, `location`, `contactNo`, `emailId`, and an
  `ExposedDropdownMenuBox` **role dropdown** (OPERATOR, TECHNICIAN, SUPERVISOR, HEAD, QC_HEAD,
  QC_MEMBER, ADMIN). `canSubmit` = `name` and `username` non-blank (+ basic email format if
  `emailId` provided). On submit builds an `MtUser` and calls back.

### 5.5 Navigation
- `navigation/Screen.kt` — add `object ManageUsers : Screen("manage_users")`.
- `navigation/NavGraph.kt` — register
  `composable(Screen.ManageUsers.route) { ManageUsersScreen(onBack = { navController.popBackStack() }) }`.
- `presentation/head/HeadHomeScreen.kt` — add a **"Manage Users" `SectionCard` tile** wired via
  a new `onManageUsers` lambda → `navController.navigate(Screen.ManageUsers.route)`.

## 6. Behavior decisions

- **Username** is entered by HEAD and stored lowercased; duplicates surface a clear
  "username already exists" message from the backend `409`.
- **Delete** requires confirmation and blocks self-deletion (a HEAD can't lock themselves out).
- **Location** is free-text (column is free-form; managed in pgAdmin today).
- **ADMIN** is authorized at the API and screen level, but the in-app entry point is the
  HeadHome tile (HEAD path). An ADMIN-specific entry can be added later if needed.
- **New users** log in with the shared fixed password; no password UI.

## 7. Error handling

| Case | Backend | Android surfacing |
|---|---|---|
| Missing name/username | 400 | inline form validation prevents submit; 400 shown if it slips through |
| Duplicate username | 409 | "username already exists" under the username field / snackbar |
| Not found (edit/delete) | 404 | error snackbar; refresh list |
| Self-delete | 400 | delete disabled for own row / error message |
| Not HEAD/ADMIN | 403 | screen shows denied state; feature not reachable in normal nav |
| Network / unknown | 5xx / exception | `Result.Error` → error state + retry |

## 8. Testing

- **Backend:** `tests/test_mt_users.py` as in §4.4 (pytest, in-memory SQLite harness).
- **Android:** manual verification against `USE_MOCK_API=true` (MockMtUserApi) for the full
  CRUD flow and role gating; then against the live backend once `/mt-users` is deployed.

## 9. File change checklist

**Backend**
- [ ] `app/schemas.py` — `MtUserDto`, `MtUserCreate`, `MtUserUpdate`
- [ ] `app/api/mt_users.py` — new router (list/get/create/update/delete + `_to_dto`)
- [ ] `app/main.py` — import + `include_router`
- [ ] `tests/test_mt_users.py` — new tests

**Android**
- [ ] `domain/model/MtUser.kt`
- [ ] `domain/repository/MtUserRepository.kt`
- [ ] `data/remote/api/MtUserApi.kt`
- [ ] `data/remote/dto/MtUserDto.kt`
- [ ] `data/remote/mock/MockMtUserApi.kt`
- [ ] `data/repository/MtUserRepositoryImpl.kt`
- [ ] `di/NetworkModule.kt` — `provideMtUserApi`
- [ ] `di/RepositoryModule.kt` — `bindMtUserRepository`
- [ ] `presentation/user/ManageUsersViewModel.kt`
- [ ] `presentation/user/ManageUsersScreen.kt`
- [ ] `presentation/user/UserFormSheet.kt`
- [ ] `navigation/Screen.kt` — `ManageUsers`
- [ ] `navigation/NavGraph.kt` — composable registration
- [ ] `presentation/head/HeadHomeScreen.kt` — Manage Users tile + `onManageUsers`
