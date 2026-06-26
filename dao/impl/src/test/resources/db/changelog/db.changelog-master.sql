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

--changeset iu-java-dao:dao-4
CREATE SCHEMA IF NOT EXISTS sql_builder_test;

--changeset iu-java-dao:dao-5
CREATE TABLE sql_builder_test.item (
    id      BIGINT PRIMARY KEY,
    code    VARCHAR(10) NOT NULL,
    label   VARCHAR(255),
    active  BOOLEAN NOT NULL DEFAULT TRUE
);

--changeset iu-java-dao:dao-6
CREATE TABLE sql_builder_test.catalog (
    id      BIGINT PRIMARY KEY,
    name    VARCHAR(100) NOT NULL
);

--changeset iu-java-dao:dao-7
CREATE TABLE sql_builder_test.catalog_detail (
    catalog_id   BIGINT PRIMARY KEY REFERENCES sql_builder_test.catalog(id),
    description  TEXT
);

--changeset iu-java-dao:dao-8
CREATE TABLE sql_builder_test.price (
    item_id   BIGINT NOT NULL,
    eff_date  DATE NOT NULL,
    amount    NUMERIC(10,2),
    PRIMARY KEY (item_id, eff_date)
);

--changeset iu-java-dao:dao-9
INSERT INTO sql_builder_test.item (id, code, label, active) VALUES
    (1, 'ITEM1', 'Seed Item', TRUE),
    (2, 'ITEM2', 'Inactive Item', FALSE);

--changeset iu-java-dao:dao-10
INSERT INTO sql_builder_test.catalog (id, name) VALUES
    (10, 'Catalog One');

--changeset iu-java-dao:dao-11
INSERT INTO sql_builder_test.catalog_detail (catalog_id, description) VALUES
    (10, 'Catalog One Description');

--changeset iu-java-dao:dao-12
INSERT INTO sql_builder_test.price (item_id, eff_date, amount) VALUES
    (1, DATE '2024-01-01', 10.00),
    (1, DATE '2024-06-01', 12.50),
    (1, DATE '2099-01-01', 13.75),
    (2, DATE '2024-01-01', 20.00);
