SET PSQL_HOME=c:\Program Files\PostgreSQL\9.4
REM SET PSQL_HOME=C:\db\PostgreSQL\9.3

SET PSQL="%PSQL_HOME%\bin\psql.exe"

%PSQL% -p5432 -c "DROP DATABASE harja;" template1
%PSQL% -p5432 -c "CREATE DATABASE harja;" template1
%PSQL% -p5432 -c "CREATE EXTENSION IF NOT EXISTS postgis;" harja
%PSQL% -p5432 -c "CREATE EXTENSION IF NOT EXISTS postgis_topology;" harja

%PSQL% -p5432 -c "CREATE USER harja;" harja
%PSQL% -p5432 -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO harja;" harja

mvn clean compile flyway:migrate

SET PGCLIENTENCODING=utf-8
%PSQL% -p5432 -f testidata.sql harja

