#!/bin/sh
cd tietokanta
psql -c "create extension postgis" -U postgres
psql -c "create database harja;" -U postgres
mvn compile -Ptravis flyway:migrate
