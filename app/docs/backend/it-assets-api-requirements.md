# Backend requirement — let `it_technician` add assets

**Requested by:** FactoryOps Android app (IT Assets feature, 2026-07-30)
**Scope:** one allow-list change. No schema change, no new endpoint.

## Context

`mt_users` already has users with `role = 'it_technician'`. The app now gives that
role an "IT Assets" section that lists `mt_asset_list` rows of
`category IN ('Electric Asset', 'IT/Communication')` and lets the user add a new
asset into the category they're viewing.

Reads already work: `GET /mt-machines` only requires an authenticated user.

## The problem

`app/api/mt_machines.py` gates creation on `_CREATE_ROLES`, which is
`SUPERVISOR / HEAD / TECHNICIAN / ADMIN`. `MtUser.norm_role` upper-cases, so an IT
user arrives as `IT_TECHNICIAN`, is not in the set, and gets:

    403 {"detail": "Not allowed to add assets"}

## The change

Add `IT_TECHNICIAN` to `_CREATE_ROLES` in `app/api/mt_machines.py`:

```python
_CREATE_ROLES = {"SUPERVISOR", "HEAD", "TECHNICIAN", "ADMIN", "IT_TECHNICIAN"}
```

Nothing else changes: `POST /mt-machines` already assigns the building-prefixed
`asset_id` server-side, stores every field, and returns the `MtMachineDto`. The app
sends `category` as the exact string `"Electric Asset"` or `"IT/Communication"`.

### Should IT be restricted to its own categories?

Your call. The app never sends anything else for this role (the category field is
read-only in the IT add form), so a server-side restriction is defence in depth
rather than a functional requirement. If you want it:

```python
_IT_CATEGORIES = {"electric asset", "it/communication"}

if user.norm_role == "IT_TECHNICIAN" and category.lower() not in _IT_CATEGORIES:
    raise HTTPException(
        status_code=status.HTTP_403_FORBIDDEN,
        detail="IT can only add Electric Asset or IT/Communication items",
    )
```

Note it would also block IT from `PUT /mt-machines/{asset_id}` only if you add the
same guard there — the app doesn't call PUT for this role today.

## Test to add

In `tests/test_mt_machines_create.py`, mirroring the existing role tests:

- an `it_technician` user POSTing a `category='IT/Communication'` asset gets **201**
  and a server-assigned `asset_id`
- an `operator` still gets **403** (unchanged)

## Verifying from the app side

Log in to the Android app as an `it_technician`, open **IT Assets → IT /
Communication → Add asset**, and submit. Before the change the sheet shows
"Not allowed to add assets"; after it, the new asset appears in the list with its
assigned id.
