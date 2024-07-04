-- Poista kokonaan turhat teidenpitoon liittyvät toimenpiteet.

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
             ('Hoito, tie',
              'Käyttö, tie (poistettu)',
              'Korvausinvestointi, tie',
              'Liikenteen hallinta, tie (poistettu)',
              'Liikenteen suunnittelupalvelu (poistettu)',
              'Purku, tie (poistettu)',
              'Tietojärjestelmät ja -infrastruktuuri (poistettu)',
              'T&K (poistettu)',
              'Uus- tai laajennusinvestointi, tie',
              'Väylänpidon omaisuushallinta (poistettu)',
              'Ylläpito, tie'));

-- Poista toimenpiteen ylätasot, taso 2
DELETE
FROM toimenpide
WHERE taso = 2
  AND id NOT IN (select emo from toimenpide)
  AND emo in
      (select tp1.id
       from toimenpide tp1
       where tp1.nimi in
             ('Hoito, tie',
              'Käyttö, tie (poistettu)',
              'Korvausinvestointi, tie',
              'Liikenteen hallinta, tie (poistettu)',
              'Liikenteen suunnittelupalvelu (poistettu)',
              'Purku, tie (poistettu)',
              'Tietojärjestelmät ja -infrastruktuuri (poistettu)',
              'T&K (poistettu)',
              'Uus- tai laajennusinvestointi, tie',
              'Väylänpidon omaisuushallinta (poistettu)',
              'Ylläpito, tie'));

-- Poista toimenpiteen ylätasot, taso 1
DELETE
FROM toimenpide
WHERE taso = 1
  AND id NOT IN (select emo from toimenpide)
  AND nimi in
      ('Hoito, tie',
       'Käyttö, tie (poistettu)',
       'Korvausinvestointi, tie',
       'Liikenteen hallinta, tie (poistettu)',
       'Liikenteen suunnittelupalvelu (poistettu)',
       'Purku, tie (poistettu)',
       'Tietojärjestelmät ja -infrastruktuuri (poistettu)',
       'T&K (poistettu)',
       'Uus- tai laajennusinvestointi, tie',
       'Väylänpidon omaisuushallinta (poistettu)',
       'Ylläpito, tie');

