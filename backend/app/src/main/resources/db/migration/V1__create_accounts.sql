-- Accounts. Emails are stored lower-cased (normalised by the application, enforced here).
CREATE TABLE app_user (
    id               uuid         PRIMARY KEY,
    email            varchar(254) NOT NULL,
    password_hash    varchar(255) NOT NULL,
    display_name     varchar(60)  NOT NULL,
    role             varchar(16)  NOT NULL,
    preferred_locale varchar(5)   NOT NULL,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT uq_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_app_user_locale CHECK (preferred_locale IN ('en', 'ja'))
);

COMMENT ON COLUMN app_user.password_hash IS 'DelegatingPasswordEncoder format, e.g. {argon2}$argon2id$...';
