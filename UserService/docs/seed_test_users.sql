-- 10 test users for the transfer flow (run after the first app start so Hibernate creates table users).
-- Fixed, readable UUIDv7 ids: 01900000-0000-7000-8000-0000000000NN  (NN = 01..10)
--
--   docker exec -i mysql-local mysql -uroot -proot123 user < UserService/docs/seed_test_users.sql
--
-- Re-run: DELETE FROM users WHERE email LIKE 'test.user%@example.com'; first.
INSERT INTO users (user_id, email, full_name, user_created_at, user_updated_at) VALUES
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000001'), 'test.user01@example.com', 'Test User 01', NOW(), NOW()),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000002'), 'test.user02@example.com', 'Test User 02', NOW(), NOW()),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000003'), 'test.user03@example.com', 'Test User 03', NOW(), NOW()),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000004'), 'test.user04@example.com', 'Test User 04', NOW(), NOW()),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000005'), 'test.user05@example.com', 'Test User 05', NOW(), NOW()),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000006'), 'test.user06@example.com', 'Test User 06', NOW(), NOW()),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000007'), 'test.user07@example.com', 'Test User 07', NOW(), NOW()),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000008'), 'test.user08@example.com', 'Test User 08', NOW(), NOW()),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000009'), 'test.user09@example.com', 'Test User 09', NOW(), NOW()),
    (UUID_TO_BIN('01900000-0000-7000-8000-000000000010'), 'test.user10@example.com', 'Test User 10', NOW(), NOW());
