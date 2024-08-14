-- Päivitetään kaikki ylläpitokohdeosat, joiden sijainnin päättely on mennyt täysin pieleen.
UPDATE yllapitokohdeosa
SET sijainti = (SELECT tierekisteriosoitteelle_viiva_ajr AS geom
                FROM tierekisteriosoitteelle_viiva_ajr(tr_numero,
                                                       tr_alkuosa,
                                                       tr_alkuetaisyys,
                                                       tr_loppuosa,
                                                       tr_loppuetaisyys,
                                                       tr_ajorata)),
    muokattu = current_timestamp
WHERE st_geometrytype(sijainti) IN ('ST_Point', 'ST_MultiPoint');
