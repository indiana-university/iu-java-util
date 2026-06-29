--liquibase formatted sql

--changeset iu-java-dao:dao-1
CREATE SCHEMA IF NOT EXISTS dao_test;

--changeset iu-java-dao:dao-2
CREATE TABLE dao_test.dao_sample (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

--changeset iu-java-dao:dao-3
INSERT INTO dao_test.dao_sample (label) VALUES ('seed');
