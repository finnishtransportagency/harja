-- Lisätään sulkutapahtumia
DO $$ DECLARE
  saimaan_urakan_id INTEGER := (SELECT id FROM urakka WHERE nimi = 'Saimaan kanava');
  saimaan_urakan_paasopimus INTEGER := (SELECT id FROM sopimus WHERE nimi = 'Saimaan huollon pääsopimus');
  kansola_id INTEGER := (SELECT id FROM kan_kohde WHERE nimi = 'Kansola');
  palli_id INTEGER := (SELECT id FROM kan_kohde WHERE nimi = 'Pälli');
  soskua_id INTEGER := (SELECT id FROM kan_kohde WHERE nimi = 'Soskua');
  joensuun_mutkan_sillat_id INTEGER := (SELECT id FROM kan_kohde WHERE nimi = 'Joensuun mutkan sillat');
  tero_id INTEGER := (SELECT id FROM kayttaja WHERE kayttajanimi='tero');

  joensuun_urakan_id INTEGER := (SELECT id FROM urakka WHERE nimi = 'Joensuun kanava');
  joensuun_urakan_paasopimus INTEGER := (SELECT id FROM sopimus WHERE nimi = 'Joensuun huollon pääsopimus');
  ahkiolahti_id INTEGER := (SELECT id FROM kan_kohde WHERE nimi = 'Ahkiolahti');
  juankoski_id INTEGER := (SELECT id FROM kan_kohde WHERE nimi = 'Juankoski');
BEGIN
SET CONSTRAINTS ALL DEFERRED;

INSERT INTO kan_liikennetapahtuma ("urakka-id", "sopimus-id", "kohde-id", aika, lisatieto, "vesipinta-alaraja", "vesipinta-ylaraja", "kuittaaja-id", luoja, poistettu)
VALUES
  (saimaan_urakan_id, saimaan_urakan_paasopimus,
   kansola_id, NOW() - INTERVAL '1 minutes', 'Testidata 1', 7100, 7300, tero_id, tero_id, false),
  (saimaan_urakan_id, saimaan_urakan_paasopimus,
   kansola_id, NOW() - INTERVAL '2 minutes', 'Testidata 3', 5000, 8400, tero_id, tero_id, false),
  (saimaan_urakan_id, saimaan_urakan_paasopimus,
   kansola_id, NOW() - INTERVAL '3 minutes', 'Testidata 4', 7007, 8010, tero_id, tero_id, TRUE),
  (saimaan_urakan_id, saimaan_urakan_paasopimus,
   kansola_id, NOW() - INTERVAL '5 minutes', 'Testidata 5', 7010, 8103, tero_id, tero_id, false),
  (saimaan_urakan_id, saimaan_urakan_paasopimus,
   palli_id, NOW(), 'Testidata 2', 7020, 8900, tero_id, tero_id, false),
  (saimaan_urakan_id, saimaan_urakan_paasopimus,
   palli_id, NOW(), 'Testidata 7', 7020, 8900, tero_id, tero_id, false),
  (saimaan_urakan_id, saimaan_urakan_paasopimus,
   soskua_id, NOW() - INTERVAL '5 minutes', 'Testidata 6', 7010, 8103, tero_id, tero_id, false),
  (saimaan_urakan_id, saimaan_urakan_paasopimus,
   joensuun_mutkan_sillat_id, NOW() - INTERVAL '15 minutes', 'Testidata 8', 7010, 8103, tero_id, tero_id, false),

   -- Joensuunkanava urakan liikennetapahtumat
   (joensuun_urakan_id, joensuun_urakan_paasopimus, ahkiolahti_id, NOW() - INTERVAL '20 minutes', 'Testidata 9', 7010, 8103, tero_id, tero_id, false),
   (joensuun_urakan_id, joensuun_urakan_paasopimus, juankoski_id, NOW() - INTERVAL '25 minutes', 'Testidata 10', 7010, 8103, tero_id, tero_id, false);
INSERT INTO kan_liikennetapahtuma_alus ("liikennetapahtuma-id", nimi, laji, lkm, matkustajalkm, suunta, nippulkm, luoja)
    VALUES
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'), 'Rohmu', 'RAH', 1, NULL, 'ylos', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'), 'Rölli', 'ÖLJ', 1, NULL, 'ylos', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), 'Antin onni', 'HUV', 4, 26, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), 'Vanha leski', 'HUV', 1, 4, 'ylos', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'), 'Merikukko', 'SEK', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'), 'Ronsu', 'LAU', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4'), 'Queen Mary', 'HIN', 1, NULL, 'ylos', 280, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6'), 'KEMIRA-12', 'ÖLJ', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 8'), '', 'ÖLJ', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 8' )),

      -- Joensuunkanavan urakan liikennetapahtuma
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 9'), '', 'ÖLJ', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 9' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 10'), '', 'HIN', 1, NULL, 'alas', NULL, (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 10' ));

INSERT INTO kan_liikennetapahtuma_toiminto ("liikennetapahtuma-id", "kohde-id", "kohteenosa-id", toimenpide, palvelumuoto, lkm, luoja)
    VALUES
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'),
       kansola_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = kansola_id AND tyyppi = 'sulku'),
      'sulutus',
      'kauko',
      1,
       tero_id),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'),
       kansola_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = kansola_id AND tyyppi = 'sulku'),
       'sulutus',
       'paikallis',
       1,
       tero_id),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4'),
       kansola_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = kansola_id AND tyyppi = 'sulku'),
       'tyhjennys',
       'kauko',
       1,
       tero_id),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 5'),
       kansola_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = kansola_id AND tyyppi = 'sulku'),
       'tyhjennys',
       'muu',
       1,
       tero_id),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'),
       palli_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = palli_id AND tyyppi = 'silta'),
       'ei-avausta',
       NULL,
       NULL,
       tero_id),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 7'),
       palli_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = palli_id AND tyyppi = 'silta'),
       'avaus',
       'itse',
       20,
       tero_id),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6'),
       soskua_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = soskua_id AND tyyppi = 'sulku'),
       'sulutus',
       'kauko',
       1,
       tero_id),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6'),
       soskua_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = soskua_id AND tyyppi = 'silta'),
       'ei-avausta',
       NULL,
       NULL,
       tero_id),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 8'),
       joensuun_mutkan_sillat_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = joensuun_mutkan_sillat_id AND nimi = 'Iso silta'),
       'ei-avausta',
       NULL,
       NULL,
       tero_id),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 8'),
       joensuun_mutkan_sillat_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = joensuun_mutkan_sillat_id AND nimi = 'Pieni silta'),
       'ei-avausta',
       NULL,
       NULL,
       tero_id),

      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 8'),
       joensuun_mutkan_sillat_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = joensuun_mutkan_sillat_id AND tyyppi = 'rautatiesilta'),
       'avaus',
       'kauko',
       1,
       tero_id),

       -- Joensuunkanava urakan liikennetapahtumat
       ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 9'),
       ahkiolahti_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = ahkiolahti_id AND tyyppi = 'sulku'),
       'sulutus',
       'paikallis',
       1,
       tero_id),

       ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 10'),
       juankoski_id,
       (SELECT id FROM kan_kohteenosa WHERE "kohde-id" = juankoski_id AND tyyppi = 'sulku'),
       'sulutus',
       'paikallis',
       1,
       tero_id);

INSERT INTO kan_liikennetapahtuma_ketjutus ("kohteelta-id", "kohteelle-id", "sopimus-id", "urakka-id","tapahtumasta-id",  "alus-id")
VALUES
  (kansola_id,
   (SELECT "ylos-id" FROM kan_kohde WHERE nimi = 'Kansola'),
   saimaan_urakan_paasopimus,
   saimaan_urakan_id,
   (SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'),
   (SELECT id FROM kan_liikennetapahtuma_alus WHERE nimi = 'Rohmu')),
  (kansola_id,
   (SELECT "ylos-id" FROM kan_kohde WHERE nimi = 'Kansola'),
   saimaan_urakan_paasopimus,
   saimaan_urakan_id,
   (SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'),
   (SELECT id FROM kan_liikennetapahtuma_alus WHERE nimi = 'Rölli')),
  (kansola_id,
   (SELECT "alas-id" FROM kan_kohde WHERE nimi = 'Kansola'),
   saimaan_urakan_paasopimus,
   saimaan_urakan_id,
   (SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'),
   (SELECT id FROM kan_liikennetapahtuma_alus WHERE nimi = 'Ronsu')),
  (kansola_id,
   (SELECT "alas-id" FROM kan_kohde WHERE nimi = 'Kansola'),
   saimaan_urakan_paasopimus,
   saimaan_urakan_id,
   (SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'),
   (SELECT id FROM kan_liikennetapahtuma_alus WHERE nimi = 'Merikukko')),
  (kansola_id,
   (SELECT "ylos-id" FROM kan_kohde WHERE nimi = 'Kansola'),
   saimaan_urakan_paasopimus,
   saimaan_urakan_id,
   (SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4'),
   (SELECT id FROM kan_liikennetapahtuma_alus WHERE nimi = 'Queen Mary')),

  (palli_id,
   (SELECT "ylos-id" FROM kan_kohde WHERE nimi = 'Pälli'),
   saimaan_urakan_paasopimus,
   saimaan_urakan_id,
   (SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'),
   (SELECT id FROM kan_liikennetapahtuma_alus WHERE nimi = 'Vanha leski')),

  (soskua_id,
   (SELECT "alas-id" FROM kan_kohde WHERE nimi = 'Soskua'),
   saimaan_urakan_paasopimus,
   saimaan_urakan_id,
   (SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 6'),
   (SELECT id FROM kan_liikennetapahtuma_alus WHERE nimi = 'KEMIRA-12'));

--COMMIT;
END $$;