SET PSQL_HOME=c:\Program Files\PostgreSQL\9.4
REM SET PSQL_HOME=C:\db\PostgreSQL\9.3

SET PSQL="%PSQL_HOME%\bin\psql.exe"

%PSQL% -p5432 -c "DROP DATABASE harjatest;" template1
%PSQL% -p5432 -c "CREATE DATABASE harjatest;" template1
%PSQL% -p5432 -c "CREATE EXTENSION IF NOT EXISTS postgis;" harjatest
%PSQL% -p5432 -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;" harjatest

%PSQL% -p5432 -c "CREATE USER harjatest;" harjatest
%PSQL% -p5432 -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO harjatest;" harjatest

mvn clean compile -Pharjatest flyway:migrate

SET PGCLIENTENCODING=utf-8
%PSQL% -p5432 -f testidata.sql harjatest

