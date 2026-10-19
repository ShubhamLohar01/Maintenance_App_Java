-- ===========================================================================
-- A-185 Preventive Maintenance Checklist (CFPLB.C4.F.41) — storage table
-- FactoryOps Android app, 2026-08-08
--
-- Mirrors doc_preventive_maintenance. The full submission (header + items[])
-- goes into `rows`; the scalar columns are duplicated for SQL reporting.
--
-- RUN THE SANITY CHECK AT THE BOTTOM OF THIS FILE FIRST.
-- ===========================================================================

BEGIN;

CREATE TABLE IF NOT EXISTS doc_preventive_maintenance_a185 (
    id          integer     PRIMARY KEY,
    month       varchar(7),                     -- YYYY-MM, from checklist_date
    checked_by  varchar(128),
    verified_by varchar(128),
    rows        jsonb       NOT NULL,           -- {form_type, doc_no, status, items:[...]}
    warehouse   varchar(16),                    -- 'A-185'
    created_by  varchar(128),
    created_at  timestamp   NOT NULL DEFAULT (now() AT TIME ZONE 'utc')
);

-- Ids must never collide with doc_preventive_maintenance:
-- GET /preventive-maintenance/checklists/{id} takes ONE integer and resolves it
-- against both registers, so the ranges are kept disjoint by starting here at
-- 1,000,000. The W-202 table would need a million rows to reach it.
CREATE SEQUENCE IF NOT EXISTS doc_preventive_maintenance_a185_id_seq
    AS integer
    START WITH 1000000
    INCREMENT BY 1
    OWNED BY doc_preventive_maintenance_a185.id;

ALTER TABLE doc_preventive_maintenance_a185
    ALTER COLUMN id SET DEFAULT nextval('doc_preventive_maintenance_a185_id_seq');

-- Newest-first listing, plus the two JSONB keys the endpoints filter on.
CREATE INDEX IF NOT EXISTS ix_pm_a185_created_at
    ON doc_preventive_maintenance_a185 (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS ix_pm_a185_status
    ON doc_preventive_maintenance_a185 ((rows ->> 'status'));
CREATE INDEX IF NOT EXISTS ix_pm_a185_form_type
    ON doc_preventive_maintenance_a185 ((rows ->> 'form_type'));

COMMIT;


-- ---------------------------------------------------------------------------
-- SANITY CHECK — run BEFORE the migration above.
-- max_existing_id must be well under 1000000. If it is not, raise START WITH
-- above it and tell the app team, because the disjoint-id assumption is what
-- lets one endpoint serve both registers.
-- ---------------------------------------------------------------------------
-- SELECT COALESCE(MAX(id), 0) AS max_existing_id FROM doc_preventive_maintenance;


-- ---------------------------------------------------------------------------
-- VERIFY — after the app submits one A-185 checklist.
-- ---------------------------------------------------------------------------
-- SELECT id, month, warehouse,
--        rows ->> 'form_type' AS form_type,
--        rows ->> 'doc_no'    AS doc_no,
--        rows ->> 'status'    AS status,
--        jsonb_array_length(rows -> 'items') AS item_count
-- FROM doc_preventive_maintenance_a185
-- ORDER BY id DESC
-- LIMIT 5;
-- Expect: id >= 1000000, doc_no 'CFPLB.C4.F.41', and item_count
--         1144 for A185_QUARTERLY or 15 for A185_MONTHLY (a complete sheet).
--         Quarterly is a big document: 161 machines x (its checkpoints + the 4
--         after-maintenance checks) ~= 170 KB of JSON per submission. Fine for
--         jsonb, but check any request-body limit in front of the API.


-- ---------------------------------------------------------------------------
-- ROLLBACK
-- ---------------------------------------------------------------------------
-- DROP TABLE IF EXISTS doc_preventive_maintenance_a185;
-- DROP SEQUENCE IF EXISTS doc_preventive_maintenance_a185_id_seq;
