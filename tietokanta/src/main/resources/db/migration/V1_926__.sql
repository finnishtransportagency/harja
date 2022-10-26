UPDATE sanktiotyyppi
SET sanktiolaji = '{vaihtosanktio,testikeskiarvo-sanktio,tenttikeskiarvo-sanktio,arvonvahennyssanktio}'
WHERE nimi = 'Ei sanktiotyyppiä' AND urakkatyyppi = '{teiden-hoito}'
