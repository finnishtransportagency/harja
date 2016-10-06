-- Poista urakoihin kopioidut toistuvat valtakunnalliset välitavoitteet,
-- joissa takaraja ennen käyttöönottoa.

DELETE FROM valitavoite vt
WHERE id IN
      (SELECT vt.id
       FROM valitavoite vt
         -- Täytyy kuulua urakkaan
         JOIN urakka u ON vt.urakka = u.id
         -- Täytyy olla linkitetty valtakunnalliseen välitavoitteeseen
         JOIN valitavoite vvt ON vt.valtakunnallinen_valitavoite = vvt.id
       WHERE vt.urakka IS NOT NULL -- Tuplavarmistus
             AND vt.valtakunnallinen_valitavoite IS NOT NULL -- Tuplavarmistus
             -- Takaraja ennen käyttöönottoa
             AND vt.takaraja <= '2016-10-01'
             -- Liittyy vuosittain toistuvaan valtakunnalliseen välitavoitteeseen
             AND vvt.takaraja_toistokuukausi IS NOT NULL
             AND vvt.takaraja_toistopaiva IS NOT NULL);