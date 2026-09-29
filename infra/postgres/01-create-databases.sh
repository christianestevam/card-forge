#!/bin/sh
# Cria um usuário e um database por serviço. Senhas fixas de desenvolvimento, só para uso local
# (ver "Débitos e desvios conscientes" no README).
set -eu

create() {
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE USER $1 WITH PASSWORD '$2';
CREATE DATABASE $1 OWNER $1;
REVOKE ALL ON DATABASE $1 FROM PUBLIC;
SQL
}

create product_service "$PRODUCT_DB_PASSWORD"
create cardholder_service "$CARDHOLDER_DB_PASSWORD"
create card_service "$CARD_DB_PASSWORD"
