-- Chạy một lần khi khởi tạo volume MySQL. User do MYSQL_USER tạo; cấp quyền cho cả hai CSDL.
CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS sign_db;
GRANT ALL PRIVILEGES ON auth_db.* TO 'signa'@'%';
GRANT ALL PRIVILEGES ON sign_db.* TO 'signa'@'%';
