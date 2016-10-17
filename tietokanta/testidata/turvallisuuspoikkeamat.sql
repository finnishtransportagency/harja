-- Oulun alueurakka 2005-2012

INSERT INTO turvallisuuspoikkeama
(lahde, vaylamuoto, urakka, tapahtunut, tila, tyontekijanammatti, kuvaus, sairauspoissaolopaivat,
sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vahinkoluokittelu, vakavuusaste,
vaarallisten_aineiden_kuljetus, vaarallisten_aineiden_vuoto, tapahtuman_otsikko)
VALUES
('harja-ui'::lahde, 'tie'::vaylamuoto, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), '2005-10-01 10:00.00', 'avoin'::turvallisuuspoikkeama_tila,
'porari'::tyontekijanammatti, 'Sepolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
7, 1, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435847, 7216217)::GEOMETRY, 6, 6, 6, 6, 6,
ARRAY['tyotapaturma']::turvallisuuspoikkeama_luokittelu[], ARRAY['henkilovahinko']::turvallisuuspoikkeama_vahinkoluokittelu[], 'lieva'::turvallisuuspoikkeama_vakavuusaste,
true, true, 'Torni kaatui');

-- Oulun alueurakka 2014-2019

INSERT INTO turvallisuuspoikkeama
(lahde, vaylamuoto, urakka, tapahtunut, tila, tyontekijanammatti, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vahinkoluokittelu, vakavuusaste, vahingoittuneet_ruumiinosat, vammat, aiheutuneet_seuraukset, sairauspoissaolo_jatkuu, turvallisuuskoordinaattori_etunimi, turvallisuuskoordinaattori_sukunimi,
 tapahtuman_otsikko, paikan_kuvaus, vaarallisten_aineiden_kuljetus, vaarallisten_aineiden_vuoto)
VALUES
  ('harja-ui'::lahde, 'tie'::vaylamuoto, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-10-01 20:00.00', 'avoin'::turvallisuuspoikkeama_tila,
                                                                    'porari'::tyontekijanammatti, 'Ernolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    7, 1, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435847, 7216217)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['tyotapaturma']::turvallisuuspoikkeama_luokittelu[], ARRAY['henkilovahinko']::turvallisuuspoikkeama_vahinkoluokittelu[], 'vakava'::turvallisuuspoikkeama_vakavuusaste,
   ARRAY['paan_alue', 'silmat']::turvallisuuspoikkeama_vahingoittunut_ruumiinosa[],
   ARRAY['sokki', 'luunmurtumat']::turvallisuuspoikkeama_aiheutuneet_vammat[],
   'Hengissä selvittiin',
   true,
   'Turvallisuuskoordinaattori Erkki', 'Esimerkki',
   'Torni kaatui Ernon päälle',
   'Outo paikka',
   true,
   true);

INSERT INTO turvallisuuspoikkeama
(lahde, vaylamuoto, urakka, tapahtunut, tila, tyontekijanammatti, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vahinkoluokittelu, vakavuusaste, tapahtuman_otsikko)
VALUES
  ('harja-ui'::lahde, 'tie'::vaylamuoto, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-10-03 10:00.00', 'avoin'::turvallisuuspoikkeama_tila,
                                                                    'porari'::tyontekijanammatti, 'Matilla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    1, 2, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435837, 7216227)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['muu']::turvallisuuspoikkeama_luokittelu[], ARRAY['henkilovahinko']::turvallisuuspoikkeama_vahinkoluokittelu[], 'vakava'::turvallisuuspoikkeama_vakavuusaste, 'Torni kaatui');

INSERT INTO turvallisuuspoikkeama
(lahde, vaylamuoto, urakka, tapahtunut, tila, tyontekijanammatti, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vahinkoluokittelu, vakavuusaste, tapahtuman_otsikko)
VALUES
  ('harja-ui'::lahde, 'tie'::vaylamuoto, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-10-05 10:00.00', 'avoin'::turvallisuuspoikkeama_tila,
                                                                    'porari'::tyontekijanammatti, 'Pentillä oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    null, null, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435817, 7216257)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['vaaratilanne', 'tyotapaturma', 'turvallisuushavainto']::turvallisuuspoikkeama_luokittelu[], ARRAY['henkilovahinko']::turvallisuuspoikkeama_vahinkoluokittelu[], 'lieva'::turvallisuuspoikkeama_vakavuusaste,
  'Torni kaatui');

INSERT INTO turvallisuuspoikkeama
(lahde, vaylamuoto, urakka, tapahtunut, tila, tyontekijanammatti, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vahinkoluokittelu, vakavuusaste, tapahtuman_otsikko)
VALUES
  ('harja-ui'::lahde, 'tie'::vaylamuoto, (SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-11-01 20:00.00', 'avoin'::turvallisuuspoikkeama_tila,
                                                                    'porari'::tyontekijanammatti, 'Jormalla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    4, 3, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435887, 7216237)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['turvallisuushavainto']::turvallisuuspoikkeama_luokittelu[], ARRAY['henkilovahinko']::turvallisuuspoikkeama_vahinkoluokittelu[], 'vakava'::turvallisuuspoikkeama_vakavuusaste, 'Torni kaatui');

INSERT INTO turvallisuuspoikkeama
(lahde, vaylamuoto, urakka, tapahtunut, tila, tyontekijanammatti, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vahinkoluokittelu, vakavuusaste, tapahtuman_otsikko)
VALUES
  ('harja-ui'::lahde, 'tie'::vaylamuoto, (SELECT id FROM urakka WHERE nimi='Kajaanin alueurakka 2014-2019'), '2015-11-01 20:00.00', 'avoin'::turvallisuuspoikkeama_tila,
                      'porari'::tyontekijanammatti, 'Ismolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                      4, 3, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435887, 7216237)::GEOMETRY, 6, 6, 6, 6, 6,
                            ARRAY['turvallisuushavainto']::turvallisuuspoikkeama_luokittelu[], ARRAY['henkilovahinko']::turvallisuuspoikkeama_vahinkoluokittelu[], 'vakava'::turvallisuuspoikkeama_vakavuusaste, 'Torni kaatui');

-- Pudasjärven alueurakka 2007-2012

INSERT INTO turvallisuuspoikkeama
(lahde, vaylamuoto, urakka, tapahtunut, tila, tyontekijanammatti, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vahinkoluokittelu, vakavuusaste, tapahtuman_otsikko)
VALUES
  ('harja-ui'::lahde, 'tie'::vaylamuoto, (SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2012-10-01 10:00.00', 'avoin'::turvallisuuspoikkeama_tila,
                                                                          'porari'::tyontekijanammatti, 'Kalevilla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    7, 1, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(227110, 6820660) :: GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['tyotapaturma']::turvallisuuspoikkeama_luokittelu[], ARRAY['henkilovahinko']::turvallisuuspoikkeama_vahinkoluokittelu[], 'lieva'::turvallisuuspoikkeama_vakavuusaste, 'Torni kaatui');

INSERT INTO korjaavatoimenpide
(turvallisuuspoikkeama, kuvaus)
VALUES
  ((SELECT id FROM turvallisuuspoikkeama WHERE tyontekijanammatti='tyonjohtaja' AND tapahtunut = '2005-10-01 10:00.00'), 'Pidetään huoli että ei kenenkään tarvi liikaa kiirehtiä');
