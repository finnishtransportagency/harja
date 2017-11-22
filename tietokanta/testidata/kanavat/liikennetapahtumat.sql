-- Lisätään sulkutapahtumia
INSERT INTO kan_liikennetapahtuma ("urakka-id", "sopimus-id", "kohde-id", aika, "sulku-toimenpide", "sulku-palvelumuoto", "sulku-lkm", lisatieto, "vesipinta-alaraja", "vesipinta-ylaraja", "kuittaaja-id", luoja, poistettu)
VALUES
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                          (SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW() - INTERVAL '1 minutes', 'sulutus', 'kauko', 1, 'Testidata 1', 7100, 7300, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                          (SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW() - INTERVAL '2 minutes', 'tyhjennys', 'paikallis', 1, 'Testidata 3', 5000, 8400, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                          (SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW() - INTERVAL '3 minutes', 'tyhjennys', 'kauko', 1, 'Testidata 4', 7007, 8010, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), TRUE),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                          (SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW() - INTERVAL '5 minutes', 'sulutus', 'itse', 15, 'Testidata 5', 7010, 8103, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false);
-- Lisätään siltatapahtumia
INSERT INTO kan_liikennetapahtuma("urakka-id", "sopimus-id", "kohde-id", aika, "silta-avaus", "silta-palvelumuoto", "silta-lkm", lisatieto, "vesipinta-alaraja", "vesipinta-ylaraja", "kuittaaja-id", luoja, poistettu)
    VALUES
      ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                              (SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren avattava ratasilta'),NOW(), TRUE, 'kauko', 1,  'Testidata 2', 7020, 8900, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false);

-- Lisätään yhdistettyjä tapahtumia
INSERT INTO kan_liikennetapahtuma ("urakka-id", "sopimus-id", "kohde-id", aika, "sulku-toimenpide", "sulku-palvelumuoto", "sulku-lkm", "silta-avaus", "silta-palvelumuoto", "silta-lkm", lisatieto, "vesipinta-ylaraja", "vesipinta-alaraja", "kuittaaja-id", luoja, poistettu)
    VALUES
      ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                              (SELECT id FROM kan_kohde WHERE nimi = 'Taipaleen sulku ja silta'),NOW() - INTERVAL '5 minutes', 'sulutus', 'kauko', 1, TRUE, 'kauko', 1, 'Testidata 6', 7010, 8103, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false);

INSERT INTO kan_liikennetapahtuma_alus ("liikennetapahtuma-id", nimi, laji, lkm, matkustajalkm, suunta, nippulkm, luoja)
    VALUES
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'), 'Rohmu', 'RAH', 1, NULL, 'ylos', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'), 'Rölli', 'ÖLJ', 1, NULL, 'ylos', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), '', 'HUV', 4, 26, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), '', 'HUV', 1, 4, 'ylos', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'), '', 'SEK', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'), 'Ronsu', 'LAU', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4'), '', 'HIN', 1, NULL, 'ylos', 280, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6'), '', 'ÖLJ', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6' ));