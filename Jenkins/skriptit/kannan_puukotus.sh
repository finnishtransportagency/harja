#!/usr/bin/env bash

psql -h localhost -U harjatest -c "ALTER TABLE yllapitokohdeosa ADD COLUMN keskimaarainen_vuorokausiliikenne INTEGER, ADD COLUMN yllapitoluokka INTEGER, ADD COLUMN nykyinen_paallyste INTEGER;"
