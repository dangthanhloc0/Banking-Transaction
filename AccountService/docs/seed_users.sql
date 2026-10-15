-- Sample data for Postman GET tests (run after the first app start so Hibernate creates table users)
INSERT INTO users (user_id, email, full_name, user_created_at, user_updated_at) VALUES
    (UUID_TO_BIN('11111111-1111-7111-8111-111111111111'), 'alice@example.com', 'Alice', NOW(), NOW()),
    (UUID_TO_BIN('22222222-2222-7222-8222-222222222222'), 'bob@example.com', 'Bob', NOW(), NOW());
