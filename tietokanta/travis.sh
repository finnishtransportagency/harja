#!/bin/sh
cd tietokanta
psql -c "create database harja;" -U postgres
mvn compile -Ptravis flyway:migrate
