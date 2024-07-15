-- Poista kokonaan turhat rautateihin ja vesiväyliin liittyvät toimenpiteet =>

-- Poista toimenpiteen ylätasot, taso 2
DELETE
FROM toimenpide
WHERE taso = 2
  AND id NOT IN (select emo from toimenpide)
  AND emo in
      (select tp1.id
       from toimenpide tp1
       where tp1.nimi in
             ('Purku, rautatie (poistettu)',
              'Purku, meri (poistettu)',
              'Merikartoitus (poistettu)',
              'Liikenteen hallinta, rautatie (poistettu)',
              'Liikenteen hallinta, meri (poistettu)',
              'Korvausinvestointi, rautatie  (poistettu)',
              'Korvausinvestointi, meri (poistettu)',
              'Käyttö, rautatie (poistettu)',
              'Käyttö, meri',
              'Julkinen liikenne ja merenkulun tuki (poistettu)',
              'Hoito, rautatie (poistettu)',
              'Hoito, meri (poistettu)',
              'Hallinto (poistettu)',
              'Talvimerenkulku, rannikko (poistettu)',
              'Talvimerenkulku, sisävedet (poistettu)',
              'Uus- tai laajennusinvestointi, meri (poistettu)',
              'Uus- tai laajennusinvestointi, rautatie (poistettu)',
              'Ylläpito, meri (poistettu)',
              'Ylläpito, rautatie (poistettu)'));

-- Poista toimenpiteen ylätasot, taso 1
DELETE
FROM toimenpide
WHERE taso = 1
  AND id NOT IN (select emo from toimenpide)
  AND nimi in
      ('Purku, rautatie (poistettu)',
       'Purku, meri (poistettu)',
       'Merikartoitus (poistettu)',
       'Liikenteen hallinta, rautatie (poistettu)',
       'Liikenteen hallinta, meri (poistettu)',
       'Korvausinvestointi, rautatie  (poistettu)',
       'Korvausinvestointi, meri (poistettu)',
       'Käyttö, rautatie (poistettu)',
       'Käyttö, meri',
       'Julkinen liikenne ja merenkulun tuki (poistettu)',
       'Hoito, rautatie (poistettu)',
       'Hoito, meri (poistettu)',
       'Hallinto (poistettu)',
       'Talvimerenkulku, rannikko (poistettu)',
       'Talvimerenkulku, sisävedet (poistettu)',
       'Uus- tai laajennusinvestointi, meri (poistettu)',
       'Uus- tai laajennusinvestointi, rautatie (poistettu)',
       'Ylläpito, meri (poistettu)',
       'Ylläpito, rautatie (poistettu)');

