UPDATE sanktiotyyppi
SET sanktiolaji = '{lupaussanktio,vaihtosanktio,testikeskiarvo-sanktio,tenttikeskiarvo-sanktio,arvonvahennyssanktio}'
WHERE nimi = 'Ei sanktiotyyppiä' AND urakkatyyppi = '{teiden-hoito}'