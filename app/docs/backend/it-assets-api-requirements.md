# Backend requirement — let `it_technician` add **and edit** assets

**Requested by:** FactoryOps Android app (IT Assets feature)
**First raised:** 2026-07-30 (add) · **Updated:** 2026-08-08 (add + edit, scoped)
**Scope:** one guard in `app/api/mt_machines.py`. No schema change, no new endpoint.

## Context

`mt_users` has users with `role = 'it_technician'`. The app gives that role an
**IT Assets** section listing `mt_asset_list` rows where
`category IN ('Electric Asset', 'IT/Communication')`. From there the user can:

- **add** an asset into the category they're viewing (shipped 2026-07-30), and
- **open an asset and edit it** (shipped 2026-08-08) — new IT asset detail screen
  with an edit sheet over the same editable columns the supervisor has.

Reads already work: `GET /mt-machines` only requires an authenticated user.

## The problem

`MtUser.norm_role` upper-cases, so an IT user arrives as `IT_TECHNICIAN`, which is
in neither allow-list:

```python
_EDIT_ROLES   = {"SUPERVISOR", "HEAD", "ADMIN"}
_CREATE_ROLES = {"SUPERVISOR", "HEAD", "TECHNICIAN", "ADMIN"}
```

So today an `it_technician` gets:

    POST /mt-machines              -> 403 {"detail": "Not allowed to add assets"}
    PUT  /mt-machines/{asset_id}   -> 403 {"detail": "Not allowed to edit assets"}

Both screens are live in the app and surface that message to the user.

## The change — category-scoped grant

The role should write **only** its own categories, so a stray or crafted call can't
touch production machinery. Add to `app/api/mt_machines.py`:

```python
# Categories an IT technician owns. Compared lower-cased; these are the exact
# mt_asset_list.category strings the app sends.
_IT_CATEGORIES = {"electric asset", "it/communication"}


def _assert_may_write(user, allowed_roles: set[str], category: str, action: str) -> None:
    """IT_TECHNICIAN may write only its own categories; other roles use the plain
    allow-list. `category` is the category the row will end up in."""
    if user.norm_role == "IT_TECHNICIAN":
        if (category or "").strip().lower() not in _IT_CATEGORIES:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=f"IT can only {action} Electric Asset or IT/Communication items",
            )
        return
    if user.norm_role not in allowed_roles:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"Not allowed to {action} assets",
        )
```

### `create_mt_machine` — replace the `_CREATE_ROLES` check

The current check happens before `category` is read, so move it just below the
`category = (payload.category or "").strip()` line:

```python
_assert_may_write(user, _CREATE_ROLES, category, "add")
```

### `update_mt_machine` — replace the `_EDIT_ROLES` check

An edit needs **both** ends checked, otherwise IT could edit its own row and set
`category = "Production Equipment"`, escaping the scope. The stored row must be
fetched before the check (today the 403 fires first — that reordering is fine, a
404 for a missing asset is a better answer than a 403 anyway):

```python
row = db.query(MtAsset).filter(MtAsset.asset_id == asset_id).first()
if row is None:
    raise HTTPException(status_code=status.HTTP_404_NOT_FOUND,
                        detail=f"Asset {asset_id} not found")

_assert_may_write(user, _EDIT_ROLES, row.category, "edit")       # where it is now
_assert_may_write(user, _EDIT_ROLES, payload.category, "edit")   # where it would move
```

Nothing else changes. `PUT` still does a full overwrite of the editable columns and
recomputes `rated_kw` from `power_load` in the response.

## What the app sends

The app never sends anything outside the scope for this role: the category field is
read-only in both the IT add sheet and the IT edit sheet, and the edit payload
carries the category from the loaded row rather than the form. The server-side check
is defence in depth.

Editable fields on the IT edit sheet — the same set the supervisor sends:
`asset_name`, `building`, `category` (unchanged), `sub_location`, `quantity`,
`model_no`, `serial_no`, `power_load`, `condition`, `remarks`.

## Known gap, not blocking

`update_mt_machine` doesn't assign `payload.assigned_to`, so that column can never be
changed through the API. The app therefore shows **Assigned to** as read-only rather
than offering a field whose value would be silently dropped. If you add
`row.assigned_to = payload.assigned_to`, tell us and we'll put the field in the sheet
— it matters more for IT assets (a laptop assigned to a person) than for machinery.

## Tests to add

`tests/test_mt_machines_create.py`:

- `it_technician` POSTing `category='IT/Communication'` → **201** with a server-assigned `asset_id`
- `it_technician` POSTing `category='Production Equipment'` → **403**
- `operator` → **403** (unchanged)

`tests/test_mt_machines_update.py`:

- `it_technician` PUTting an `Electric Asset` row → **200**
- `it_technician` PUTting a `Production Equipment` row → **403**
- `it_technician` PUTting an `IT/Communication` row with `category='Production Equipment'`
  in the body → **403** (can't move it out of scope)
- `supervisor` on any category → **200** (unchanged)

## Verifying from the app side

Log in to the Android app as an `it_technician`:

1. **IT Assets → IT / Communication → Add asset** → submit. Before the change the
   sheet reads "Not allowed to add assets"; after, the asset appears with its id.
2. **IT Assets → Electrical → tap any asset → ✎ → change Condition → Save changes.**
   Before the change the sheet reads "Not allowed to edit assets"; after, the sheet
   closes, an "Asset updated" snackbar shows, and the new value is on the record.
