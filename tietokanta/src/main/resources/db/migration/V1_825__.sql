UPDATE sanktiotyyppi
SET nimi = 'Ei tarvita sanktiotyyppiä'
WHERE nimi = 'Ei sanktiotyyppiä' AND urakkatyyppi = '{teiden-hoito}'
