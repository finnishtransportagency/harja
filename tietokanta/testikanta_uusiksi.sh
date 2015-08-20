#!/bin/sh

psql=psql

$psql -p5432 -c "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity WHERE pg_stat_activity.datname = 'harjatest' AND pid <> pg_backend_pid();"
$psql -p5432 -c "DROP DATABASE harjatest;"
$psql -p5432 -c "CREATE DATABASE harjatest OWNER harjatest TEMPLATE harjatest_template;"
