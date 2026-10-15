-- 10,000 load-test accounts, one per load-test user (see UserService/docs/seed_10k_users.sql).
--   account_id     = 01900000-0000-7000-8001-<12-digit n>
--   user_id        = 01900000-0000-7000-8000-<12-digit n>
--   account_number = '10' + 8-digit n      (n = 1  -> 1000000001, same pattern as the old 10 accounts)
--   balance        = 1,000,000.00, ACTIVE        total = 10,000,000,000.00
--
--   1) run docs/indexes.sql of this service FIRST (unique index on account_number)
--   2) docker exec -i mysql-local mysql -uroot -proot123 account < AccountService/docs/seed_10k_accounts.sql
SET SESSION cte_max_recursion_depth = 20000;

INSERT INTO accounts (account_id, user_id, account_number, account_balance, account_status, account_created_at, account_updated_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT UUID_TO_BIN(CONCAT('01900000-0000-7000-8001-', LPAD(n, 12, '0'))),
       UUID_TO_BIN(CONCAT('01900000-0000-7000-8000-', LPAD(n, 12, '0'))),
       CONCAT('10', LPAD(n, 8, '0')),
       1000000.00,
       'ACTIVE',
       NOW(),
       NOW()
FROM seq;
