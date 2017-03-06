#!/bin/sh

#sudo /etc/init.d/postgresql stop
#wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
#sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt/ precise-pgdg main 9.5" >> /etc/apt/sources.list.d/postgresql.list'
#sudo apt-get update
#sudo apt-get install postgresql-9.5
sudo apt-get install postgresql-9.5-postgis-2.2

sudo /etc/init.d/postgresql restart

sudo sh tietokanta/travis_pg_conf.sh

sudo /etc/init.d/postgresql restart

cd tietokanta
psql -c "CREATE USER harjatest WITH CREATEDB;" -U postgres
psql -c "CREATE ROLE harja;" -U postgres
psql -c "ALTER USER harjatest WITH SUPERUSER;" -U postgres
psql -c "CREATE DATABASE temp OWNER harjatest;" -U postgres
psql -c "CREATE DATABASE harjatest_template OWNER harjatest;" -U postgres
psql -c "CREATE EXTENSION postgis" -U postgres harjatest_template
psql -c "CREATE EXTENSION postgis_topology" -U postgres harjatest_template
psql -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO harjatest;" -U postgres

mvn compile -Ptravis flyway:migrate

psql -U harjatest harjatest_template -X -q -a -l -v ON_ERROR_STOP=1 --pset pager=off -f testidata.sql > /dev/null

psql -U harjatest -c "CREATE DATABASE harjatest OWNER harjatest TEMPLATE harjatest_template;" harjatest_template
