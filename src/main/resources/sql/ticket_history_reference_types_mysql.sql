-- Manual pre-deployment review only. Isolated tests do not verify the production schema.
-- Do not assume whether the existing column is VARCHAR or native ENUM.
SELECT COLUMN_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ticket_ledger'
  AND COLUMN_NAME = 'ref_type';

-- VARCHAR(30) already holds GROUP_MATCHING and MILESTONE_REWARD; no change needed.
-- For a native ENUM, compare its actual existing values before using the template
-- below. Preserve any additional values present in the real schema. Validate on
-- an isolated MySQL copy first; DDL is not part of the application transaction.
-- The template is commented out deliberately and is not run automatically.
-- ALTER TABLE ticket_ledger MODIFY COLUMN ref_type ENUM(
--     'IAP_ORDER', 'IAP_REFUND', 'AD_REWARD_SESSION',
--     'MATCHING_RECOMMENDATION', 'MATCHING_CONNECT', 'ADMIN_ADJUSTMENT',
--     'GROUP_MATCHING', 'MILESTONE_REWARD', 'FESTIVAL_COUPON'
-- ) NOT NULL;
