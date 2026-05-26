#!/bin/bash
# Cross-schema grants for Aurora MES — runs as postgres superuser on first container start.
# Grants work_order_user access to iam schema objects so V007__seed_item_master_privileges.sql
# can INSERT into iam.privilege and iam.role_privilege once the IAM service has run its migrations.
set -e

WORK_ORDER_USER="${WORK_ORDER_DB_USER:-work_order_user}"
WORK_ORDER_PASS="${WORK_ORDER_DB_PASSWORD:-changeme}"

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${WORK_ORDER_USER}') THEN
            CREATE ROLE "${WORK_ORDER_USER}" WITH LOGIN PASSWORD '${WORK_ORDER_PASS}';
        END IF;
    END
    \$\$;

    -- Allow work_order_user to create its own schema in the mes database
    GRANT CONNECT, CREATE ON DATABASE "${POSTGRES_DB}" TO "${WORK_ORDER_USER}";

    -- Pre-create iam schema so the GRANT USAGE and ALTER DEFAULT PRIVILEGES can be set
    -- before the IAM service Flyway runs. Tables are created later by iam-service.
    CREATE SCHEMA IF NOT EXISTS iam;
    GRANT USAGE ON SCHEMA iam TO "${WORK_ORDER_USER}";

    -- Grant read/write on all future tables that the IAM service (running as $POSTGRES_USER)
    -- creates in the iam schema. This covers iam.privilege and iam.role_privilege.
    ALTER DEFAULT PRIVILEGES FOR ROLE "${POSTGRES_USER}" IN SCHEMA iam
        GRANT SELECT, INSERT ON TABLES TO "${WORK_ORDER_USER}";
EOSQL
