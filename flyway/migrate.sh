#!/usr/bin/env bash

DB_URL="jdbc:postgresql://172.17.0.3:5432/todolist"
DB_USER="postgres"
DB_PASSWORD="postgres"

echo "Migrating database at ${DB_URL}"

docker run --rm \
  --network="host" \
  -e FLYWAY_URL="${DB_URL}" \
  -e FLYWAY_USER="${DB_USER}" \
  -e FLYWAY_PASSWORD="${DB_PASSWORD}" \
  -v "./migrations":/flyway/sql \
  -v "./conf/flyway.conf":/flyway/conf/flyway.conf \
   flyway/flyway:7.15-alpine \
   migrate
