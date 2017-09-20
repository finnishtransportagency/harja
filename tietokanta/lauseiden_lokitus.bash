#!/usr/bin/env bash

docker exec harjadb bash -c 'echo "log_statement = all" >>  /etc/postgresql/9.5/main/postgresql.conf; kill -HUP `cat /run/postgresql/9.5-main.pid`; tail -f /var/log/postgresql/postgresql-9.5-main.log'

