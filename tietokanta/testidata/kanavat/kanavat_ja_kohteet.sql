-- Kanavien ja kohteiden testidata

INSERT INTO kan_kohde (nimi, tyyppi, "kanava-id", luoja)
    VALUES
      (NULL, 'sulku' :: kohteen_tyyppi, (SELECT id FROM kan_kanava WHERE NIMI = 'Keiteleen kanava: Kapeenkosken kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      (NULL, 'sulku' :: kohteen_tyyppi, (SELECT id FROM kan_kanava WHERE NIMI = 'Keiteleen kanava: Kuhankosken kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      ('Tikkalansaaren avattava ratasilta', 'silta' :: kohteen_tyyppi, (SELECT id FROM kan_kanava WHERE NIMI = 'Taipaleen kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),
      (NULL, 'sulku-ja-silta' :: kohteen_tyyppi, (SELECT id FROM kan_kanava WHERE NIMI = 'Nerkoon kanava'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'));

INSERT INTO kan_kohde_urakka ("kohde-id", "urakka-id", luoja)
    VALUES
      ((SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren avattava ratasilta'),
       (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'),
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero'));