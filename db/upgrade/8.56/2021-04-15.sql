ALTER TABLE login MODIFY last_login TIMESTAMP;
UPDATE login SET last_login = NULL where last_login = 0000-00-00;

ALTER TABLE `ant`.`description_homonym` MODIFY created timestamp DEFAULT CURRENT_TIMESTAMP NULL;
UPDATE ant.description_homonym SET created = NULL where created = 0000-00-00;