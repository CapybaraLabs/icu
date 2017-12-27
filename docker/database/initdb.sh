#!/bin/bash
set -e

while ! psql -U postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname='$POSTGRES_USER'" | grep -q 1; do
    echo "Waiting on postgres own initial setup to finish"
    sleep 1
done
sleep 1
while ! pg_isready -U postgres; do
    echo "Waiting on postgres to be ready"
    sleep 1
done

# make sure the icu user exists
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -tAc "SELECT 1 FROM pg_roles WHERE rolname='icu'" | grep -q 1 || createuser -U "$POSTGRES_USER" icu

# make sure the icu database exists
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -tc "SELECT 1 FROM pg_database WHERE datname = 'icu';" | grep -q 1 || psql -U "$POSTGRES_USER" -c "CREATE DATABASE icu WITH OWNER = icu;"
# make sure the icu database is owned by the user icu
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -c "ALTER DATABASE icu OWNER TO icu;"
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -c "GRANT ALL PRIVILEGES ON DATABASE icu TO icu;"
# make sure HSTORE extension is enabled
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d icu -c "CREATE EXTENSION IF NOT EXISTS hstore;"
