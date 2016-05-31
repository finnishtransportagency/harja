#!/bin/sh

sudo /etc/init.d/postgresql stop
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt/ precise-pgdg main 9.5" >> /etc/apt/sources.list.d/postgresql.list'
sudo apt-get update
sudo apt-get install postgresql-9.5
sudo apt-get install postgresql-9.5-postgis

echo "listen_addresses = '*'" >> /etc/postgresql/9.5/main/postgresql.conf
/etc/init.d/postgresql restart


echo "KATSOTAAN ONKO POSTGRES KÄYNNISSÄ"
ps axf | grep postgres

echo "KATSOTAAN POSTGRES HAKEMISTOJA"
ls /var/lib/postgresql/9.5/main

echo "POSTGRES.CONF"
cat /etc/postgresql/9.5/main/postgresql.conf



sleep 5

cd tietokanta
psql -c "create database harja;" -U postgres
psql -c "create extension postgis" -U postgres harja
mvn compile -Ptravis flyway:migrate
