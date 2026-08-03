-- Align the two W-202 meter tables with mt_a185_readings, their sibling feature.
--
-- They were created with `warehouse TEXT DEFAULT 'W202'`, but mt_a185_readings uses
-- `plant` holding the hyphenated 'A-185'. Matching it means the new router can be a
-- straight copy of a185_readings.py, the app's plant string passes through untranslated,
-- and a report unioning both plants' readings needs no special case.
--
-- Safe to run: both tables are empty. The unique indexes follow the rename automatically;
-- the trigger functions do NOT (a plpgsql body is stored as text), so both are recreated
-- below with the new column name. Everything else about them is unchanged.

BEGIN;

-- ── Main meter ───────────────────────────────────────────────────────────────
ALTER TABLE mt_w202_meterreading RENAME COLUMN warehouse TO plant;
ALTER TABLE mt_w202_meterreading ALTER COLUMN plant SET DEFAULT 'W-202';
UPDATE mt_w202_meterreading SET plant = 'W-202' WHERE plant <> 'W-202';

CREATE OR REPLACE FUNCTION mt_w202_meterreading_derive() RETURNS trigger AS $$
DECLARE
    prev_kwh  NUMERIC(14,2);
    prev_kvah NUMERIC(14,2);
BEGIN
    IF NEW.reading_date IS NOT NULL
       AND (NEW.initial_kwh IS NULL OR NEW.initial_kvah IS NULL) THEN
        SELECT final_kwh, final_kvah
          INTO prev_kwh, prev_kvah
          FROM mt_w202_meterreading
         WHERE plant = NEW.plant
           AND reading_date IS NOT NULL
           AND reading_date < NEW.reading_date
         ORDER BY reading_date DESC
         LIMIT 1;

        NEW.initial_kwh  := COALESCE(NEW.initial_kwh,  prev_kwh);
        NEW.initial_kvah := COALESCE(NEW.initial_kvah, prev_kvah);
    END IF;

    NEW.kwh_consumption := CASE
        WHEN NEW.initial_kwh IS NULL OR NEW.final_kwh IS NULL THEN NULL
        ELSE NEW.final_kwh - NEW.initial_kwh
    END;

    NEW.kvah_consumption := CASE
        WHEN NEW.initial_kvah IS NULL OR NEW.final_kvah IS NULL THEN NULL
        ELSE NEW.final_kvah - NEW.initial_kvah
    END;

    NEW.power_factor := COALESCE(NEW.power_factor, 0.98);

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── Solar ────────────────────────────────────────────────────────────────────
ALTER TABLE mt_w202solar_reading RENAME COLUMN warehouse TO plant;
ALTER TABLE mt_w202solar_reading ALTER COLUMN plant SET DEFAULT 'W-202';
UPDATE mt_w202solar_reading SET plant = 'W-202' WHERE plant <> 'W-202';

CREATE OR REPLACE FUNCTION mt_w202solar_reading_derive() RETURNS trigger AS $$
BEGIN
    NEW.kwh_consumption := CASE
        WHEN NEW.initial_kwh IS NULL OR NEW.final_kwh IS NULL THEN NULL
        ELSE NEW.final_kwh - NEW.initial_kwh
    END;

    NEW.kvah_consumption := CASE
        WHEN NEW.initial_kvah IS NULL OR NEW.final_kvah IS NULL THEN NULL
        ELSE NEW.final_kvah - NEW.initial_kvah
    END;

    NEW.power_factor := CASE
        WHEN NEW.kwh_consumption IS NULL
          OR NEW.kvah_consumption IS NULL
          OR NEW.kvah_consumption = 0 THEN NULL
        ELSE ROUND(NEW.kwh_consumption / NEW.kvah_consumption, 3)
    END;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMIT;
