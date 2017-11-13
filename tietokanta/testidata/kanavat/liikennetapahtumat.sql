INSERT INTO kan_liikennetapahtuma ("urakka-id", "sopimus-id", "kohde-id", aika, toimenpide, palvelumuoto, "palvelumuoto-lkm", lisatieto, "vesipinta-ylaraja", "vesipinta-alaraja", "kuittaaja-id", luoja, poistettu)
VALUES
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                          (SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW() - INTERVAL '1 minutes', 'sulutus', 'kauko', 1, 'Testidata 1', 7100, 7300, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                          (SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren avattava ratasilta'),NOW(), 'sillan-avaus', NULL, NULL,  'Testidata 2', 7020, 8900, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                          (SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW() - INTERVAL '2 minutes', 'tyhjennys', 'paikallis', 1, 'Testidata 3', 5000, 8400, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                          (SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW() - INTERVAL '3 minutes', 'tyhjennys', 'kauko', 1, 'Testidata 4', 7007, 8010, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), TRUE),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
                                                          (SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW() - INTERVAL '5 minutes', 'sulutus', 'itse', 15, 'Testidata 5', 7010, 8103, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false);

INSERT INTO kan_liikennetapahtuma_alus ("liikennetapahtuma-id", nimi, laji, lkm, matkustajalkm, suunta, luoja)
    VALUES
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'), 'Rohmu', 'RAH', 1, NULL, 'ylos', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'), 'Rölli', 'ÖLJ', 1, NULL, 'ylos', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), '', 'HUV', 4, 26, 'alas', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), '', 'HUV', 1, 4, 'ylos', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'), '', 'SEK', 1, NULL, 'alas', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'), 'Ronsu', 'LAU', 1, NULL, 'alas', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4'), '', 'HINT', 1, NULL, 'ylos', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 5' ));

INSERT INTO kan_liikennetapahtuma_nippu ("liikennetapahtuma-id", lkm, suunta, luoja)
    VALUES
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), 8, 'ylos', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4' ));