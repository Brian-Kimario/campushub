#!/bin/bash
# Creates the least-privilege database users (DATA_MODEL §12, DEPLOYMENT §5).
# Runs once, on first initialization of the data volume, from /docker-entrypoint-initdb.d.
# Passwords are inserted into SQL literals: they must not contain single quotes.
set -euo pipefail

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<EOSQL
CREATE USER IF NOT EXISTS '${CAMPUSHUB_DB_APP_USER}'@'%' IDENTIFIED BY '${CAMPUSHUB_DB_APP_PASSWORD}';
CREATE USER IF NOT EXISTS '${CAMPUSHUB_DB_MIGRATION_USER}'@'%' IDENTIFIED BY '${CAMPUSHUB_DB_MIGRATION_PASSWORD}';

-- Runtime user: data manipulation only. It cannot change the schema.
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${MYSQL_DATABASE}\`.* TO '${CAMPUSHUB_DB_APP_USER}'@'%';

-- Migration user: schema changes, used only by the Flyway step.
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO '${CAMPUSHUB_DB_MIGRATION_USER}'@'%';

FLUSH PRIVILEGES;
EOSQL
