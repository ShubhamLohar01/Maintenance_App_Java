# Daily Utility Consumption — Design

**Date:** 2026-07-15
**Module:** `presentation/utilities` (new)
**Scope:** Android/Java frontend only. Backend (`/utilities/*` endpoints + tables) is built by the backend team from the prompt in the appendix.

## 1. Overview

A data-entry + history feature for four daily utility consumptions: **Diesel, Gas, Electricity, Water**. The technician picks a plant and date, types the raw **input** readings, and the app **auto-calculates** the derived fields live (read-only) using fixed formulas. Submitting upserts the record to the backend (unique per `plant + reading_date`). Each utility also has a **history list** of stored entries.

Reached from the existing **Daily Reading** hub (Technician dashboard). The old floor-meter-readings "Electric" page is left in the codebase but is no longer linked from the hub (parked, may be restored later).

## 2. Navigation

- `DailyReadingHub` rows become (renamed/reordered): **Diesel → Gas → Electricity → Water**. Each row opens that utility's **History screen**.
- Routes (new):
  - `utilities/{util}` — history list, `util` ∈ `diesel|gas|electricity|water`
  - `utilities/{util}/form?date={date}&plant={plant}` — add/edit form; no args → blank (date=today, plant=user's plant); args → edit that record.
- The Electricity hub row points to `utilities/electricity` (the new form), NOT the old `daily_reading` route. The old `DailyReading`/`DailyReadingScreen` composable + route stay in code, unreferenced from the hub.

## 3. Screens (one pattern × 4 utilities)

### 3a. History screen
- Top: plant filter chips **A-185 / W-202 / All** (default All).
- Body: `LazyColumn` of stored entries (newest first), each card showing `reading_date`, `plant`, the utility's headline totals (e.g. total cost, consumption) and `cost_per_unit` (or "—").
- FAB / button **"＋ Add reading"** → opens form blank (date=today, plant=user's plant).
- Tapping a card → opens form prefilled in **edit** mode (same record; re-submit upserts).
- Loads via `GET /utilities/{util}` (optional `?plant=`). Empty/error → empty state + retry.

### 3b. Form screen
- **Plant selector** (A-185 / W-202 — sent verbatim), **date picker** → `reading_date` "YYYY-MM-DD".
- **Editable numeric inputs** (per utility, below) with sensible defaults.
- **Read-only computed fields** — greyed/disabled, recomputed on **every keystroke**.
- **Submit** button, enabled only when validation passes. On 2xx: use returned row to refresh, snackbar "Saved", pop to history.

## 4. Utilities — inputs, defaults, formulas, JSON

Shared body fields: `plant`, `reading_date`.

### Diesel — `POST /utilities/diesel`
Inputs: `initial_kwh_reading`, `final_kwh_reading`, `start_dg_run_hour`, `stop_dg_run_hour`, `diesel_l_per_hour` (default 37.5), `diesel_rate` (default 95 ₹/L), `diesel_received_l`, `remark` (text).
Computed (read-only):
- `total_consumption = final_kwh_reading − initial_kwh_reading`
- `total_run_hour = stop_dg_run_hour − start_dg_run_hour`
- `total_diesel_l = diesel_l_per_hour × total_run_hour`
- `total_fuel_cost = total_diesel_l × diesel_rate`

### Gas — `POST /utilities/gas`
Inputs: `gas_meter_opening`, `gas_meter_closing`, `gas_conversion_factor` (default 1.44), `gas_rate` (₹/m³), `production_units`, `remark`.
Computed:
- `gas_consumed_m3 = (gas_meter_closing − gas_meter_opening) × gas_conversion_factor`
- `daily_gas_cost = gas_consumed_m3 × gas_rate`
- `cost_per_unit = production_units > 0 ? daily_gas_cost / production_units : null`

### Electricity — `POST /utilities/electricity`
Inputs: `department` (text, optional), `energy_meter_opening_kwh`, `energy_meter_closing_kwh`, `energy_meter_opening_kvah`, `energy_meter_closing_kvah`, `ct_multiplier` (default 4), `electricity_rate` (₹/kWh), `production_units`, `remark`.
Computed:
- `electricity_consumed_kwh = (energy_meter_closing_kwh − energy_meter_opening_kwh) × ct_multiplier`
- `electricity_consumed_kvah = energy_meter_closing_kvah − energy_meter_opening_kvah`
- `daily_electricity_cost = electricity_consumed_kwh × electricity_rate`
- `cost_per_unit = production_units > 0 ? daily_electricity_cost / production_units : null`

### Water — `POST /utilities/water`
Inputs: `water_meter_opening`, `water_meter_closing`, `water_rate` (₹/m³), `production_units`, `remark`.
Computed:
- `water_consumed = water_meter_closing − water_meter_opening`
- `daily_water_cost = water_consumed × water_rate`
- `cost_per_unit = production_units > 0 ? daily_water_cost / production_units : null`

Each JSON body sends **all** input + computed fields (keys exactly as above), plus `plant`, `reading_date`.

## 5. Rounding & edge cases

- **Rounding** (display AND value sent): money fields (`*_cost`, `*_rate`, `cost_per_unit`) → **2 dp**; consumption/quantity fields (`total_consumption`, `total_run_hour`, `total_diesel_l`, `gas_consumed_m3`, `electricity_consumed_*`, `water_consumed`, meter/production inputs) → **3 dp**.
- **Division by zero:** `production_units` empty or 0 → display "—", send `cost_per_unit = null` (never 0 or Infinity).
- **Validation (Submit gate):** every closing ≥ its opening; all required inputs present and numeric. `department`/`remark` optional.
- Negative/invalid → keep Submit disabled, show inline hint.

## 6. Code structure

- `data/remote/api/UtilitiesApi.kt` — 4× `@POST` + 4× `@GET`, `@Header("Authorization")`, real backend only (DI in `NetworkModule` like `PmSyncApi`, no mock).
- `data/remote/dto/UtilityDtos.kt` — request+response `@Serializable` DTOs per utility (`cost_per_unit: Double?` nullable; response mirrors request + `id`, `created_at`).
- `domain/model/` — `UtilityType` enum + domain records per utility (or one sealed model). `domain/repository/UtilitiesRepository.kt`.
- `data/repository/UtilitiesRepositoryImpl.kt` — `submitX(...)` + `listX(plant?)` per utility; token from `AuthPreferences`; try/catch → `Result`.
- **Compute — pure functions** in `domain/usecase/utilities/UtilityMath.kt` (e.g. `computeDiesel`, `computeGas`, …) returning the computed set; unit-tested (mirrors `computeRunKwh`).
- Presentation `presentation/utilities/`: `UtilityHub*` reuse existing hub; per utility a `XHistoryScreen` + `XFormScreen` + `XViewModel`; shared composables `PlantSelector`, `DateField`, `NumericInputField`, `ComputedField`, `PlantFilterChips`.

## 7. Testing

- Unit tests for each `computeX` (happy path, zero production → null cost, rounding boundaries, closing<opening).
- DTO serialization round-trip test (nullable `cost_per_unit`).
- ViewModel validation-gate test (Submit enabled/disabled).

## 8. Out of scope (now)

- Offline queue/local persistence (submit is online-only; show retry on network fail). Can add later like the energy SyncWorker.
- Reports/aggregation over utilities.
- Localization (matches current Daily Reading which is English-only).

## Appendix — Backend prompt (for the backend team's session)

> Build a **Daily Utility Consumption** API. Four utilities, four tables, one router `prefix="/utilities"`, existing Bearer-JWT auth (`get_current_user`).
>
> **Tables** (RDS/Postgres) — `mt_utility_diesel`, `mt_utility_gas`, `mt_utility_electricity`, `mt_utility_water`. Common columns: `id` PK, `plant` (str, "A-185"/"W-202"), `reading_date` (Date), `remark` (Text, null), `created_by` (username), `created_at`, `updated_at`. **UNIQUE (plant, reading_date)** on each. Plus the per-utility columns (all Numeric/Float, computed ones stored too; `cost_per_unit` nullable):
>   - diesel: initial_kwh_reading, final_kwh_reading, start_dg_run_hour, stop_dg_run_hour, diesel_l_per_hour, diesel_rate, diesel_received_l, total_consumption, total_run_hour, total_diesel_l, total_fuel_cost
>   - gas: gas_meter_opening, gas_meter_closing, gas_conversion_factor, gas_rate, production_units, gas_consumed_m3, daily_gas_cost, cost_per_unit (nullable)
>   - electricity: department (str null), energy_meter_opening_kwh, energy_meter_closing_kwh, energy_meter_opening_kvah, energy_meter_closing_kvah, ct_multiplier, electricity_rate, production_units, electricity_consumed_kwh, electricity_consumed_kvah, daily_electricity_cost, cost_per_unit (nullable)
>   - water: water_meter_opening, water_meter_closing, water_rate, production_units, water_consumed, daily_water_cost, cost_per_unit (nullable)
>
> **Endpoints** (per utility `{util}`):
>   - `POST /utilities/{util}` — body = all fields above (incl. computed). **Upsert on (plant, reading_date)**: insert if new, else update in place (set `updated_at`). `cost_per_unit` may be null — store null (never 0/Infinity). Return the stored row (same keys + id/created_at) with 200/201.
>   - `GET /utilities/{util}?plant=` — list stored rows newest first (by reading_date desc, id desc); optional `plant` filter ("A-185"/"W-202"); omit → all. Return [] when empty.
>
> Validation: 400 if `reading_date` missing/unparseable or any closing < opening. Keep request/response keys EXACTLY as listed (snake_case) — the Android app depends on them.
