--liquibase formatted sql
--changeset iu-jdbc:1.0.0

create table iu_jdbc_pool_test (
	value varchar(80)
);
