#!/bin/sh

### HUOM: tämä ajetaan postgres docker kontin sisällä ###

cd /tietokanta

psql -h "$POSTGRES_PORT_5432_TCP_ADDR" -p "$POSTGRES_PORT_5432_TCP_PORT" -U harja harja -X -q -a -v ON_ERROR_STOP=1 --pset pager=off -f testidata.sql
