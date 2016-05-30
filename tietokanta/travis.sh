#!/bin/sh
cd tietokanta
psql -c "create database harja;" -U postgres
psql -c "create extension postgis" -U postgres harja
mvn compile -Ptravis flyway:migrate
