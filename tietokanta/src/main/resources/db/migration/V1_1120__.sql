-- Poista kokonaan tehtävät jotka liittyvät toimenpiteisiin, joita ei ole kytketty toimenpideinstanssiin ja joihin ei ole tehty toteumakirjauksia
DELETE
FROM tehtava
WHERE emo IN (select id
              from toimenpide
              where taso = 3
                and id not in (select toimenpide from toimenpideinstanssi))
  AND id NOT IN (select tehtava_id FROM rahavaraus_tehtava)
  AND id NOT IN (select toimenpidekoodi FROM toteuma_tehtava);

-- Poista kokonaan turhat rautateihin ja vesiväyliin liittyvät toimenpiteet =>

-- Poista toimenpiteet, joilla ei ole tehtäviä ja jotka eivät kytkeydy toimenpideinstansseihin
DELETE
FROM toimenpide
WHERE taso = 3
  AND id NOT IN (select emo from tehtava where emo is not null)
  AND id NOT IN (select toimenpide from toimenpideinstanssi)
  AND emo in
      (select tp2.id
       from toimenpide tp2
                join toimenpide tp1 on tp2.emo = tp1.id
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

