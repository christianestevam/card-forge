#!/bin/sh
set -eu
for service in product cardholder card; do
 psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<SQL
CREATE USER ${service} WITH PASSWORD '${service}_local';
CREATE DATABASE ${service} OWNER ${service};
SQL
done
