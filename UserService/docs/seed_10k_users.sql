-- 10,000 load-test users.
--   user_id = 01900000-0000-7000-8000-<12-digit n>    n = 1..10000
--   email   = loadtest.user<5-digit n>@example.com
--
--   docker exec -i mysql-local mysql -uroot -proot123 user < UserService/docs/seed_10k_users.sql
--
-- The recursion depth must be larger than the row count (default limit is 1000).
SET SESSION cte_max_recursion_depth = 20000;

INSERT INTO users (user_id, email, full_name, user_created_at, user_updated_at)
WITH RECURSIVE seq AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 10000
)
SELECT UUID_TO_BIN(CONCAT('01900000-0000-7000-8000-', LPAD(n, 12, '0'))),
       CONCAT('loadtest.user', LPAD(n, 5, '0'), '@example.com'),
       CONCAT('Load Test User ', LPAD(n, 5, '0')),
       NOW(),
       NOW()
FROM seq;
