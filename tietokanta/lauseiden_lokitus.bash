#!/usr/bin/env bash

docker exec harjadb bash -c 'echo "log_statement = all" >>  /var/lib/pgsql/12/data/postgresql.conf; kill -HUP `cat /var/lib/pgsql/12/data/postmaster.pid`; tail -f /var/log/postgresql/postgresql-12-main.log'

