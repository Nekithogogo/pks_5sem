#!/bin/sh
set -eu

# На первом запуске создаём отдельную базу КР 3 по сохранённому снимку КР 1.
# Если база уже существует, пользовательские данные никогда не заменяются.
db_name=${KR1_DATABASE_NAME:-autosalon_kr1}
case "$db_name" in ''|*[!a-zA-Z0-9_]*) echo 'Invalid database name' >&2; exit 1;; esac
exists=$(psql -X -t -A -v ON_ERROR_STOP=1 -d postgres -c "SELECT 1 FROM pg_database WHERE datname='$db_name'")
if [ "$exists" = "1" ]; then
  tables=$(psql -X -t -A -v ON_ERROR_STOP=1 -d "$db_name" -c "SELECT (to_regclass('public.clients') IS NOT NULL AND to_regclass('public.cars') IS NOT NULL AND to_regclass('public.test_drive_requests') IS NOT NULL)::int")
  if [ "$tables" != "1" ]; then
    echo 'Database autosalon_kr1 exists but its original tables are incomplete; no data was changed.' >&2
    exit 1
  fi
  echo 'Existing KR3 database found; preserving its data.'
  exit 0
fi
createdb "$db_name"
psql -X -q -v ON_ERROR_STOP=1 --single-transaction -d "$db_name" -f /seed/V1__base.sql -f /seed/kr1-data.sql
echo 'KR1 schema and snapshot imported into a new KR3 database.'
