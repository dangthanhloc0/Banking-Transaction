-- Runs once, the first time the mysql volume is created. Tables are created by Hibernate (ddl-auto=update).
CREATE DATABASE IF NOT EXISTS `user`        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS `account`     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS `transaction` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
