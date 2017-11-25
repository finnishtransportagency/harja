-- Lisätään sulkutapahtumia
BEGIN;
SET CONSTRAINTS ALL DEFERRED;

INSERT INTO kan_liikennetapahtuma ("urakka-id", "sopimus-id", "kohde-id", aika, lisatieto, "vesipinta-alaraja", "vesipinta-ylaraja", "kuittaaja-id", luoja, poistettu)
VALUES
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
   (SELECT id FROM kan_kohde WHERE nimi = 'Kansola'),NOW() - INTERVAL '1 minutes', 'Testidata 1', 7100, 7300, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
   (SELECT id FROM kan_kohde WHERE nimi = 'Kansola'),NOW() - INTERVAL '2 minutes', 'Testidata 3', 5000, 8400, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
   (SELECT id FROM kan_kohde WHERE nimi = 'Kansola'),NOW() - INTERVAL '3 minutes', 'Testidata 4', 7007, 8010, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), TRUE),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
   (SELECT id FROM kan_kohde WHERE nimi = 'Kansola'),NOW() - INTERVAL '5 minutes', 'Testidata 5', 7010, 8103, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
   (SELECT id FROM kan_kohde WHERE nimi = 'Pälli'), NOW(), 'Testidata 2', 7020, 8900, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
   (SELECT id FROM kan_kohde WHERE nimi = 'Pälli'), NOW(), 'Testidata 7', 7020, 8900, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
   (SELECT id FROM kan_kohde WHERE nimi = 'Soskua'),NOW() - INTERVAL '5 minutes', 'Testidata 6', 7010, 8103, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM urakka WHERE nimi = 'Saimaan kanava'), (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus'),
   (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'),NOW() - INTERVAL '15 minutes', 'Testidata 8', 7010, 8103, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false);

INSERT INTO kan_liikennetapahtuma_alus ("liikennetapahtuma-id", nimi, laji, lkm, matkustajalkm, suunta, nippulkm, luoja)
    VALUES
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'), 'Rohmu', 'RAH', 1, NULL, 'ylos', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'), 'Rölli', 'ÖLJ', 1, NULL, 'ylos', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), '', 'HUV', 4, 26, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), '', 'HUV', 1, 4, 'ylos', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'), '', 'SEK', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'), 'Ronsu', 'LAU', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4'), '', 'HIN', 1, NULL, 'ylos', 280, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6'), '', 'ÖLJ', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 8'), '', 'ÖLJ', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6' ));

INSERT INTO kan_liikennetapahtuma_osa ("liikennetapahtuma-id", "kohde-id", "kohteenosa-id", toimenpide, palvelumuoto, lkm, luoja)
    VALUES
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Kansola'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Kansola') AND tyyppi = 'sulku'),
      'sulutus',
      'kauko',
      1,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Kansola'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Kansola') AND tyyppi = 'sulku'),
       'sulutus',
       'paikallis',
       1,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Kansola'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Kansola') AND tyyppi = 'sulku'),
       'tyhjennys',
       'kauko',
       1,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 5'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Kansola'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Kansola') AND tyyppi = 'sulku'),
       'tyhjennys',
       'muu',
       1,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Pälli'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Pälli') AND tyyppi = 'silta'),
       'ei-avausta',
       NULL,
       NULL,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 7'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Pälli'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Pälli') AND tyyppi = 'silta'),
       'avaus',
       'itse',
       20,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Soskua'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Soskua') AND tyyppi = 'sulku'),
       'sulutus',
       'kauko',
       1,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Soskua'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Soskua') AND tyyppi = 'silta'),
       'ei-avausta',
       NULL,
       NULL,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 8'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat') AND nimi = 'Iso silta'),
       'ei-avausta',
       NULL,
       NULL,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 8'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat') AND nimi = 'Pieni silta'),
       'ei-avausta',
       NULL,
       NULL,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero')),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 8'),
       (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat'),
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat') AND tyyppi = 'rautatiesilta'),
       'avaus',
       'kauko',
       1,
       (SELECT id FROM kayttaja WHERE kayttajanimi='tero'));
COMMIT;