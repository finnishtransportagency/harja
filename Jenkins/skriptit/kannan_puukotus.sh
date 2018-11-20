#!/usr/bin/env bash

# Esim. tällaisen komennon voi ajaa kantaan migraatioiden jälkeen, jos on tarvesta. Tämä lähinnä sellaisten haarojen testaamiseen, joissa on
# migraatioita, joita ei haluta ajaa vielä kantaan. Silloin migraatioiden muutokset täytyy käydä puukottamassa.
# psql -h localhost -U harjatest harjatest_template -c "ALTER TABLE yllapitokohdeosa ADD COLUMN keskimaarainen_vuorokausiliikenne INTEGER, ADD COLUMN yllapitoluokka INTEGER, ADD COLUMN nykyinen_paallyste INTEGER;"