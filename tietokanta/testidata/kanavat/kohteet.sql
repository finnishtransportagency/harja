-- Kanavien ja kohteiden testidata

INSERT INTO kan_kohdekokonaisuus (nimi, luoja)
    VALUES
      ('Iisalmen reitti', (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('Saimaan kanava', (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('Joensuun reitti', (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('Sipoon ojat', (SELECT id FROM kayttaja WHERE kayttajanimi='tero'));

INSERT INTO kan_kohde(nimi, "kohdekokonaisuus-id", luoja)
    VALUES
      ('Pälli',(SELECT id FROM kan_kohdekokonaisuus WHERE nimi = 'Saimaan kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('Kansola', (SELECT id FROM kan_kohdekokonaisuus WHERE nimi = 'Saimaan kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('Soskua', (SELECT id FROM kan_kohdekokonaisuus WHERE nimi = 'Saimaan kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ('Joensuun kanava',(SELECT id FROM kan_kohdekokonaisuus WHERE nimi = 'Joensuun reitti'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('Joensuun mutkan sillat',(SELECT id FROM kan_kohdekokonaisuus WHERE nimi = 'Joensuun reitti'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ('Iisalmen kanava',(SELECT id FROM kan_kohdekokonaisuus WHERE nimi = 'Iisalmen reitti'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'));

INSERT INTO kan_kohteenosa (tyyppi, nimi, oletuspalvelumuoto, "kohde-id", luoja, sijainti)
    VALUES
      ('silta', NULL, 'itse', (SELECT id FROM kan_kohde WHERE nimi = 'Pälli'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(430925.930308596 7198503.58736702)'),

      ('sulku', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Kansola'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(410925.930308596 7498503.58736702)'),

      ('sulku', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Soskua'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(420525.930308596 7198503.58736702)'),
      ('silta', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Soskua'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(420925.930308596 7199503.58736702)'),

      ('sulku', NULL, 'paikallis', (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(440925.930308596 7108503.58736702)'),
      ('silta', NULL, 'paikallis', (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(440525.930308596 7103503.58736702)'),
      ('rautatiesilta', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(430925.930308596 7198503.58736702)'),

      ('silta', 'Iso silta', NULL, (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(490025.930308596 7198503.58736702)'),
      ('silta', 'Pieni silta', NULL, (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(490725.930308596 7190503.58736702)'),
      ('rautatiesilta', NULL, NULL, (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(490625.930308596 7198503.58736702)'),

      ('sulku', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Iisalmen kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(450925.930308596 7198503.58736702)'),
      ('silta', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Iisalmen kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), 'POINT(450025.930308596 7198503.58736702)');

-- Ketjutus
UPDATE kan_kohde
  SET
    "ylos-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Kansola')
WHERE nimi = 'Pälli';

UPDATE kan_kohde
SET
  "ylos-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Soskua'),
  "alas-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Pälli')
WHERE nimi = 'Kansola';

UPDATE kan_kohde
SET
  "alas-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Kansola')
WHERE nimi = 'Soskua';


INSERT INTO kan_kohde_urakka ("kohde-id", "urakka-id", luoja)
VALUES
  ((SELECT id FROM kan_kohde WHERE nimi = 'Pälli'),
   (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'),
   (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
  ((SELECT id FROM kan_kohde WHERE nimi = 'Kansola'),
   (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'),
   (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
  ((SELECT id FROM kan_kohde WHERE nimi = 'Soskua'),
   (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'),
   (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

  ((SELECT id FROM kan_kohde WHERE nimi = 'Joensuun kanava'),
   (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'),
   (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

  ((SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'),
   (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'),
   (SELECT id FROM kayttaja WHERE kayttajanimi='tero'));