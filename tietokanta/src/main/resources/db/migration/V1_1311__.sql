-- Päivitetään lupaustyypit. Monivalinnat käsitellään niin, että toteumaksi tulee kaikkien kuukausien keskiarvo. Päätöskuukausi 0 = kaikki kuukaudet vaikuttavat.
-- Tämä muuttaa toteuman laskentalogiikan kaikilta Maksuehto-lupaukselta ja korjaa laskentalogiikan vastaamaan käyttöliittymässä informoituja vaikuttavia kuukausia muilla.
UPDATE lupaus
SET lupaustyyppi = 'monivalinta',
    "paatos-kk" = '{0}'
WHERE kuvaus in ('Maksuehto', 'Kohdistetun seurannan parantaminen', 'Ajoneuvokohtainen ajotavanseurantajärjestelmä',
                 'Tilanne- ja ennakkotiedotus')
  AND "urakan-alkuvuosi" in (2025, 2026);
