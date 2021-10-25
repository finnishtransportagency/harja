#!/usr/bin/env bash

docker exec harjadb bash -c 'echo "log_statement = all" >>  /var/lib/pgsql/12/data/postgresql.conf; su - postgres -c "/usr/pgsql-12/bin/pg_ctl reload"; tail -f /var/lib/pgsql/12/data/log/postgresql-$(date +%a).log'
