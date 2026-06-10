-- Päivitetään ei-suorasanktioille käsittelytavaksi ei-tiedossa
UPDATE sanktio s
   SET kasittelytapa = 'ei-tiedossa'::laatupoikkeaman_kasittelytapa
 WHERE s.kasittelytapa IS NULL
   AND s.suorasanktio IS FALSE;

