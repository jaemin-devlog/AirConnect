-- Synthetic baseline. ONLY the opt-in disposable airconnect_admin_verify database.
-- The harness has already created current tables; remove the newly introduced schema
-- so the actual release migrations, not Hibernate update, must supply it.
DROP TABLE admin_ticket_adjustments;
ALTER TABLE maintenance_settings DROP COLUMN version;
INSERT INTO maintenance_settings (id,enabled,title,message,updated_at)
VALUES (1,false,'legacy fixture title','legacy fixture message','2026-09-01 01:02:03');
-- Exercise the supported existing VARCHAR branch. Native ENUM needs a separately
-- reviewed value-preserving ALTER for the real deployment, never a guessed list.
ALTER TABLE ticket_ledger MODIFY COLUMN ref_type VARCHAR(30) NOT NULL;
