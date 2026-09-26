-- V2__align_enum_check_constraints.sql
--
-- The first migration that actually changes anything, and the one that proves Flyway
-- now owns the schema rather than Hibernate.
--
-- WHY THIS EXISTS
--
-- Hibernate wrote a CHECK constraint for every @Enumerated(EnumType.STRING) column --
-- 74 of them, one per enum-backed column. ddl-auto: update creates those constraints
-- when it creates the table and then never touches them again, in either direction.
-- So the moment an enum gains a value, the database still refuses it and inserts fail
-- at runtime with a constraint violation, on a database that "updated" without complaint.
--
-- All 74 were compared against their Java enums. The result was not what the tracker
-- claimed: nothing is currently being blocked. Every constraint permits exactly what its
-- enum declares, with one exception in the opposite direction, which is the evidence
-- that update really does ignore these constraints:
--
--   stock_movements.movement_type permits SALE_RETURN, and MovementType no longer
--   declares it. The value was removed from the enum and the constraint kept it.
--
-- WHAT THIS DOES
--
-- Tightens that one constraint to match MovementType's eight values. A constraint that
-- permits a value the application cannot produce is dead permission: it cannot be
-- reached through the app, and it quietly suggests a feature that is not there.
--
-- Safe to tighten: stock_movements holds 29 rows across TRANSFER_OUT, TRANSFER_IN,
-- ADJUSTMENT_IN, SALE and ADJUSTMENT_OUT, and none uses SALE_RETURN. Verified before
-- writing this.
--
-- IF SALE_RETURN IS EVER WANTED BACK -- a credit note returning goods is a fair reason --
-- add it to MovementType and ship a migration alongside that change. That pairing is the
-- whole point of moving schema to Flyway: the enum and the constraint move together,
-- explicitly, instead of drifting apart silently.

ALTER TABLE stock_movements
    DROP CONSTRAINT IF EXISTS stock_movements_movement_type_check;

ALTER TABLE stock_movements
    ADD CONSTRAINT stock_movements_movement_type_check
    CHECK (movement_type IN (
        'OPENING',
        'PURCHASE',
        'ADJUSTMENT_IN',
        'TRANSFER_IN',
        'SALE',
        'ADJUSTMENT_OUT',
        'TRANSFER_OUT',
        'WRITE_OFF'
    ));
