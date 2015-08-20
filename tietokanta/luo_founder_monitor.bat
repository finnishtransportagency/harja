"c:\Program Files\PostgreSQL\9.4\bin\psql.exe" -p5432 -c "DROP DATABASE monitor;" template1
"c:\Program Files\PostgreSQL\9.4\bin\psql.exe" -p5432 -c "CREATE DATABASE monitor;" template1

"c:\Program Files\PostgreSQL\9.4\bin\psql.exe" -p5432 -c "CREATE USER monitor;" monitor
"c:\Program Files\PostgreSQL\9.4\bin\psql.exe" -p5432 -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO monitor;" monitor

SET PGCLIENTENCODING=utf-8
"c:\Program Files\PostgreSQL\9.4\bin\psql.exe" -p5432 -f src/main/resources/db/scripts/founder_monitor.sql monitor

