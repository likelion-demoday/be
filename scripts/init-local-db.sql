-- 로컬 개발용 DB와 계정 생성
-- Docker(docker-compose.local.yml)를 쓰면 자동으로 만들어지므로 실행할 필요가 없다.
-- 로컬에 MySQL을 직접 설치해 쓰는 경우에만 root 계정으로 한 번 실행한다.
--
--   mysql -u root -p < scripts/init-local-db.sql
--
-- 비밀번호는 각자 로컬 값으로 바꾸고, 바꾼 값을 application-local.yml 에 동일하게 적는다.

CREATE DATABASE IF NOT EXISTS resay
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'resay'@'localhost' IDENTIFIED BY 'resay';
GRANT ALL PRIVILEGES ON resay.* TO 'resay'@'localhost';
FLUSH PRIVILEGES;
