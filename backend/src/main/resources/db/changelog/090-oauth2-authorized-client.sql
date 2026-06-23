--liquibase formatted sql

--changeset oriol:oauth2-authorized-client-001
--comment Spring Security JDBC store for OAuth2 authorized clients (Google refresh tokens for Calendar/Gmail/Drive)
CREATE TABLE oauth2_authorized_client (
    client_registration_id  VARCHAR(100)  NOT NULL,
    principal_name          VARCHAR(200)  NOT NULL,
    access_token_type       VARCHAR(100)  NOT NULL,
    access_token_value      BYTEA         NOT NULL,
    access_token_issued_at  TIMESTAMP     NOT NULL,
    access_token_expires_at TIMESTAMP     NOT NULL,
    access_token_scopes     VARCHAR(1000) DEFAULT NULL,
    refresh_token_value     BYTEA         DEFAULT NULL,
    refresh_token_issued_at TIMESTAMP     DEFAULT NULL,
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (client_registration_id, principal_name)
);
--rollback DROP TABLE oauth2_authorized_client;
