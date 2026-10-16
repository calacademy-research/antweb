-- Accounts for a fresh MySQL 8.4 data volume (runs once, from /docker-entrypoint-initdb.d).
-- Same accounts and rights as on MySQL 5.7; mysql_native_password needs --mysql-native-password=ON.
-- Production also has netdata@'172.0.0.0/255.0.0.0' and pulse_ro@localhost; those are
-- recreated at cutover from their existing password hashes (not stored here).
CREATE USER IF NOT EXISTS 'antweb'@'localhost' IDENTIFIED WITH mysql_native_password BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON *.* TO 'antweb'@'localhost' WITH GRANT OPTION;
CREATE USER IF NOT EXISTS 'antweb'@'%' IDENTIFIED WITH mysql_native_password BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON *.* TO 'antweb'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
