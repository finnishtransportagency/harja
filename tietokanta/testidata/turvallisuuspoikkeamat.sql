INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, sairauspoissaolopaivat,
sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vakavuusaste)
VALUES
((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2005-2012'), '2005-10-01 10:00.00', '2005-10-01 12:20.00', '2005-10-06 09:00.00',
'Trukkikuski', 'Lastaus', 'Sepolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
7, 1, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435847, 7216217)::GEOMETRY, 6, 6, 6, 6, 6,
ARRAY['tyotapaturma']::turvallisuuspoikkeama_luokittelu[], 'lieva'::turvallisuuspoikkeama_vakavuusaste);

INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vakavuusaste)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-10-01 20:00.00', '2015-10-01 22:20.00', '2015-10-06 23:00.00',
                                                                    'Trukkikuski', 'Lastaus', 'Sepolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    7, 1, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435847, 7216217)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['tyotapaturma']::turvallisuuspoikkeama_luokittelu[], 'vakava'::turvallisuuspoikkeama_vakavuusaste);

INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vakavuusaste)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-10-03 10:00.00', '2015-10-03 12:20.00', '2015-10-06 23:00.00',
                                                                    'Trukkikuskina ajaminen', 'Lastauksen tekeminen', 'Matilla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    1, 2, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435837, 7216227)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['muu']::turvallisuuspoikkeama_luokittelu[], 'vakava'::turvallisuuspoikkeama_vakavuusaste);

INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vakavuusaste)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-10-05 10:00.00', '2015-10-05 12:20.00', '2015-10-07 23:00.00',
                                                                    'Trukkikuskina toimiminen', 'Lastailu', 'Pentillä oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    null, null, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435817, 7216257)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['vaaratilanne', 'tyotapaturma', 'turvallisuushavainto']::turvallisuuspoikkeama_luokittelu[], 'lieva'::turvallisuuspoikkeama_vakavuusaste);

INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vakavuusaste)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Oulun alueurakka 2014-2019'), '2015-11-01 20:00.00', '2015-11-01 22:20.00', '2015-11-06 23:00.00',
                                                                    'Trukkikuskeilu', 'Lastaaminen', 'Jormalla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    4, 3, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(435887, 7216237)::GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['turvallisuushavainto']::turvallisuuspoikkeama_luokittelu[], 'vakava'::turvallisuuspoikkeama_vakavuusaste);


INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, sairauspoissaolopaivat,
 sairaalavuorokaudet, luotu, luoja, sijainti, tr_numero, tr_alkuosa, tr_loppuosa, tr_alkuetaisyys, tr_loppuetaisyys, tyyppi, vakavuusaste)
VALUES
  ((SELECT id FROM urakka WHERE nimi='Pudasjärven alueurakka 2007-2012'), '2012-10-01 10:00.00', '2012-10-01 12:20.00', '2012-10-06 09:00.00',
                                                                    'Trukkikuski', 'Lastaus', 'Sepolla oli kiire lastata laatikot, ja torni kaatui päälle. Ehti onneksi pois alta niin ei henki lähtenyt.',
                                                                    7, 1, NOW(), (SELECT id FROM kayttaja WHERE kayttajanimi='jvh'), ST_MakePoint(227110, 6820660) :: GEOMETRY, 6, 6, 6, 6, 6,
   ARRAY['tyotapaturma']::turvallisuuspoikkeama_luokittelu[], 'lieva'::turvallisuuspoikkeama_vakavuusaste);

INSERT INTO korjaavatoimenpide
(turvallisuuspoikkeama, kuvaus, vastaavahenkilo)
VALUES
  ((SELECT id FROM turvallisuuspoikkeama WHERE tyontekijanammatti='tyonjohtaja' AND tapahtunut = '2005-10-01 10:00.00'), 'Pidetään huoli että ei kenenkään tarvi liikaa kiirehtiä',
   'Tomi Työnjohtaja');
