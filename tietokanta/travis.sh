#!/bin/sh

sudo /etc/init.d/postgresql stop
wget --quiet -O - https://www.postgresql.org/media/keys/ACCC4CF8.asc | sudo apt-key add -
sudo sh -c 'echo "deb http://apt.postgresql.org/pub/repos/apt/ precise-pgdg main 9.5" >> /etc/apt/sources.list.d/postgresql.list'
sudo apt-get update
sudo apt-get install postgresql-9.5
sudo apt-get install postgresql-9.5-postgis

cd tietokanta
psql -c "create database harja;" -U postgres
psql -c "create extension postgis" -U postgres harja
mvn compile -Ptravis flyway:migrate
