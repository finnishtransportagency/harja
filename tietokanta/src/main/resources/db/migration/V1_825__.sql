UPDATE sanktiotyyppi
SET nimi = 'Sanktiotyyppiä ei tarvita'
WHERE nimi = 'Ei sanktiotyyppiä' AND urakkatyyppi = '{teiden-hoito}'
