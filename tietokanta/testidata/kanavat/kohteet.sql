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

INSERT INTO kan_kohteenosa (tyyppi, nimi, oletuspalvelumuoto, "kohde-id", luoja)
    VALUES
      ('silta', NULL, 'itse', (SELECT id FROM kan_kohde WHERE nimi = 'Pälli'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('sulku', 'Pällin sulku', 'itse', (SELECT id FROM kan_kohde WHERE nimi = 'Pälli'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ('sulku', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Kansola'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ('sulku', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Soskua'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('silta', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Soskua'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ('sulku', NULL, 'paikallis', (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('silta', NULL, 'paikallis', (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('rautatiesilta', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ('silta', 'Iso silta', NULL, (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('silta', 'Pieni silta', NULL, (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('rautatiesilta', NULL, NULL, (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ('sulku', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Iisalmen kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('silta', NULL, 'kauko', (SELECT id FROM kan_kohde WHERE nimi = 'Iisalmen kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'));

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