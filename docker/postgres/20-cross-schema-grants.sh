#!/bin/bash
# Cross-schema grants for Aurora MES — runs as postgres superuser on first container start.
# Creates service DB users and grants them access to the iam schema so Flyway privilege-seed
# migrations can INSERT into iam.privilege and iam.role_privilege once iam-service has run.
set -e

WORK_ORDER_USER="${WORK_ORDER_DB_USER:-work_order_user}"
WORK_ORDER_PASS="${WORK_ORDER_DB_PASSWORD:-changeme}"
INVENTORY_USER="${INVENTORY_DB_USER:-inventory_user}"
INVENTORY_PASS="${INVENTORY_DB_PASSWORD:-changeme}"
ENGINEERING_USER="${ENGINEERING_DB_USER:-engineering_user}"
ENGINEERING_PASS="${ENGINEERING_DB_PASSWORD:-changeme}"
QUALITY_USER="${QUALITY_DB_USER:-quality_user}"
QUALITY_PASS="${QUALITY_DB_PASSWORD:-changeme}"
LABOUR_USER="${LABOUR_DB_USER:-labour_user}"
LABOUR_PASS="${LABOUR_DB_PASSWORD:-changeme}"

psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${WORK_ORDER_USER}') THEN
            CREATE ROLE "${WORK_ORDER_USER}" WITH LOGIN PASSWORD '${WORK_ORDER_PASS}';
        END IF;
    END
    \$\$;

    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${INVENTORY_USER}') THEN
            CREATE ROLE "${INVENTORY_USER}" WITH LOGIN PASSWORD '${INVENTORY_PASS}';
        END IF;
    END
    \$\$;

    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${ENGINEERING_USER}') THEN
            CREATE ROLE "${ENGINEERING_USER}" WITH LOGIN PASSWORD '${ENGINEERING_PASS}';
        END IF;
    END
    \$\$;

    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${QUALITY_USER}') THEN
            CREATE ROLE "${QUALITY_USER}" WITH LOGIN PASSWORD '${QUALITY_PASS}';
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${LABOUR_USER}') THEN
            CREATE ROLE "${LABOUR_USER}" WITH LOGIN PASSWORD '${LABOUR_PASS}';
        END IF;
    END
    \$\$;

    -- Allow service users to create their own schemas in the mes database
    GRANT CONNECT, CREATE ON DATABASE "${POSTGRES_DB}" TO "${WORK_ORDER_USER}";
    GRANT CONNECT, CREATE ON DATABASE "${POSTGRES_DB}" TO "${INVENTORY_USER}";
    GRANT CONNECT, CREATE ON DATABASE "${POSTGRES_DB}" TO "${ENGINEERING_USER}";
    GRANT CONNECT, CREATE ON DATABASE "${POSTGRES_DB}" TO "${QUALITY_USER}";
    GRANT CONNECT, CREATE ON DATABASE "${POSTGRES_DB}" TO "${LABOUR_USER}";

    -- Pre-create iam schema so GRANT USAGE and ALTER DEFAULT PRIVILEGES can be set
    -- before the IAM service Flyway runs. Tables are created later by iam-service.
    CREATE SCHEMA IF NOT EXISTS iam;
    GRANT USAGE ON SCHEMA iam TO "${WORK_ORDER_USER}";
    GRANT USAGE ON SCHEMA iam TO "${INVENTORY_USER}";
    GRANT USAGE ON SCHEMA iam TO "${ENGINEERING_USER}";
    GRANT USAGE ON SCHEMA iam TO "${QUALITY_USER}";
    GRANT USAGE ON SCHEMA iam TO "${LABOUR_USER}";

    -- Grant read/write on all future tables that the IAM service (running as $POSTGRES_USER)
    -- creates in the iam schema. Covers iam.privilege, iam.role_privilege, iam.role.
    ALTER DEFAULT PRIVILEGES FOR ROLE "${POSTGRES_USER}" IN SCHEMA iam
        GRANT SELECT, INSERT ON TABLES TO "${WORK_ORDER_USER}";
    ALTER DEFAULT PRIVILEGES FOR ROLE "${POSTGRES_USER}" IN SCHEMA iam
        GRANT SELECT, INSERT ON TABLES TO "${INVENTORY_USER}";
    ALTER DEFAULT PRIVILEGES FOR ROLE "${POSTGRES_USER}" IN SCHEMA iam
        GRANT SELECT, INSERT ON TABLES TO "${ENGINEERING_USER}";
    ALTER DEFAULT PRIVILEGES FOR ROLE "${POSTGRES_USER}" IN SCHEMA iam
        GRANT SELECT, INSERT ON TABLES TO "${QUALITY_USER}";
        GRANT SELECT, INSERT ON TABLES TO "${LABOUR_USER}";
EOSQL
