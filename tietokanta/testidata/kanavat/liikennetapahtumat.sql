INSERT INTO kan_liikennetapahtuma ("kohde-id", aika, toimenpide, palvelumuoto, "palvelumuoto-lkm", lisatieto, "vesipinta-ylaraja", "vesipinta-alaraja", "kuittaaja-id", luoja, poistettu)
VALUES
  ((SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW(), 'sulutus', 'kauko', 1, 'Testidata 1', 7000, 8000, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren avattava ratasilta'),NOW(), 'sillan-avaus', NULL, NULL,  'Testidata 2', 7000, 8000, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW(), 'sulutus', 'paikallis', 1, 'Testidata 3', 7000, 8000, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false),
  ((SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW(), 'tyhjennys', 'kauko', 1, 'Testidata 4', 7000, 8000, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), TRUE),
  ((SELECT id FROM kan_kohde WHERE nimi = 'Tikkalansaaren sulku'),NOW(), 'sulutus', 'itse', 15, 'Testidata 5', 7000, 8000, (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), (SELECT id FROM kayttaja WHERE kayttajanimi='tero'), false);

INSERT INTO kan_liikennetapahtuma_alus ("liikennetapahtuma-id", nimi, laji, lkm, matkustajalkm, suunta, luoja)
    VALUES
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'), 'Rohmu', 'RAH', 1, NULL, 'ylös', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1'), 'Rölli', 'ÖLJ', 1, NULL, 'ylös', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 1' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), '', 'HUV', 4, 26, 'alas', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'), '', 'SEK', 1, NULL, 'alas', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3'), 'Ronsu', 'LAU', 1, NULL, 'alas', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 3' )),
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4'), '', 'HINT', 1, NULL, 'ylös', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 5' ));

INSERT INTO kan_liikennetapahtuma_nippu ("liikennetapahtuma-id", lkm, suunta, luoja)
    VALUES
      ((SELECT id FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 2'), 8, 'ylös', (SELECT luoja FROM kan_liikennetapahtuma WHERE lisatieto = 'Testidata 4' ));