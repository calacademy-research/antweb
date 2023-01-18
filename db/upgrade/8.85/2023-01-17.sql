
alter table server add column debug varchar(64);

create table user_agentBak as select * from user_agent;

delete from user_agent;

ALTER TABLE user_agent ADD PRIMARY KEY(name)

CREATE TABLE user_agent_whitelist (
  name varchar(500) NOT NULL primary key,
  created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8
;

