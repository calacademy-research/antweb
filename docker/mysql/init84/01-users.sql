-- Accounts for a fresh MySQL 8.4 data volume (runs once, from /docker-entrypoint-initdb.d).
-- mysql_native_password needs --mysql-native-password=ON.
-- Production also has netdata@'172.0.0.0/255.0.0.0', pulse_ro@localhost and the API's read-only
-- antweb_api login; those are recreated by hand (passwords are never stored here).
--
-- antweb@localhost: reachable only inside the MySQL container; full rights for backups and
-- maintenance (mysqldump, restores, schema changes run by hand).
CREATE USER IF NOT EXISTS 'antweb'@'localhost' IDENTIFIED WITH mysql_native_password BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON *.* TO 'antweb'@'localhost' WITH GRANT OPTION;
-- antweb@'%': the application. Rights on the ant database only: no global privileges (no FILE,
-- no CREATE USER, no other databases) and no GRANT OPTION. Same as set on the servers on 2026-10-16.
CREATE USER IF NOT EXISTS 'antweb'@'%' IDENTIFIED WITH mysql_native_password BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON `ant`.* TO 'antweb'@'%';
FLUSH PRIVILEGES;
