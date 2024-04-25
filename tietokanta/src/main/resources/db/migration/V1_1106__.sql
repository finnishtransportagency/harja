-- Rahavaraustehtäviin on lipsahtanut vääriä tehtäviä.
-- Poistetaan ne kaikki
DELETE FROM rahavaraus_tehtava;

-- Ja lisätään ne uudestaan

INSERT
  INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE yksiloiva_tunniste IN (
                              '1f12fe16-375e-49bf-9a95-4560326ce6cf', -- Äkillinen hoitotyö (talvihoito)
                              '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974', -- Äkillinen hoitotyö (l.ymp.hoito)
                              'd373c08b-32eb-4ac2-b817-04106b862fb1' -- Äkillinen hoitotyö (soratiet)
     )
   AND rv.nimi = 'Äkilliset hoitotyöt';

INSERT
  INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE yksiloiva_tunniste IN (
                              '49b7388b-419c-47fa-9b1b-3797f1fab21d', -- Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (talvihoito),
                              '63a2585b-5597-43ea-945c-1b25b16a06e2', --Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (l.ymp.hoito),
                              'b3a7a210-4ba6-4555-905c-fef7308dc5ec' --Kolmansien osapuolten aiheuttamien vahinkojen korjaaminen (soratiet),
     )
   AND rv.nimi = 'Vahinkojen korvaukset';

INSERT
  INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'
   AND rv.nimi = 'Kannustinjärjestelmä';

INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM tehtava,
       rahavaraus rv
 WHERE tehtava.nimi IN ('AB-paikkaus levittäjällä',
                        'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)',
                        'Avo-ojitus/soratiet (kaapeli kaivualueella)',
                        'Avo-ojitus/soratiet',
                        'KT-reikävaluasfalttipaikkaus',
                        'KT-valuasfalttipaikkaus K',
                        'KT-valuasfalttipaikkaus T',
                        'KT-valuasfalttisaumaus',
                        'Kaiteiden poisto ja uusiminen',
                        'Kalliokynsien louhinta ojituksen yhteydessä',
                        'Kannukaatosaumaus',
                        'Katupölynsidonta',
                        'Käsin tehtävät paikkaukset pikapaikkausmassalla',
                        'Laskuojat/päällystetyt tiet',
                        'Laskuojat/soratiet',
                        'Muut päällystettyjen teiden sorapientareiden kunnossapitoon liittyvät työt',
                        'Opastustaulujen ja opastusviittojen uusiminen portaaliin',
                        'Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)',
                        'Opastustaulun/-viitan uusiminen',
                        'PAB-paikkaus käsin',
                        'Pysäkkikatoksen uusiminen',
                        'Pysäkkikatoksen poistaminen',
                        'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm',
                        'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm',
                        'Reunantäyttö',
                        'Reunapalteen poisto kaiteen alta',
                        'Reunapalteen poisto',
                        'Runkopuiden poisto',
                        'Sorateitä kaventava ojitus',
                        'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm',
                        'Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm',
                        'Soratien runkokelirikkokorjaukset',
                        'Töherrysten estokäsittely',
                        'Töherrysten poisto',
                        'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)',
                        'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)',
                        'Vakiokokoisten liikennemerkkien uusiminen,  pelkkä merkki',
                        'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet',
                        'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet',
                        'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet',
                        'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet')
   AND rv.nimi = 'Rahavaraus A'
   AND "mhu-tehtava?" = TRUE
   AND tehtava.poistettu = FALSE;

-- Tilaajan rahavaraus B
INSERT
  INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rahavaraus.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus,
       tehtava
-- Äkilliset hoitotyöt
 WHERE tehtava.yksiloiva_tunniste = '1ed5d0bb-13c7-4f52-91ee-5051bb0fd974'
   AND rahavaraus.nimi = 'Rahavaraus B';


-- Tilaajan rahavaraus C
INSERT
  INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rahavaraus.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus,
       tehtava
-- Kolmansien osapuolien vahingot
 WHERE yksiloiva_tunniste = '63a2585b-5597-43ea-945c-1b25b16a06e2'
   AND rahavaraus.nimi = 'Rahavaraus B';


INSERT
  INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE tehtava.nimi = 'Pysäkkikatosten ja niiden varusteiden vaurioiden kuntoon saattaminen'
   AND rv.nimi = 'Rahavaraus D - Levähdys- ja P-alueet';


INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE tehtava.nimi = 'Levähdys- ja P-alueiden varusteiden vaurioiden kuntoon saattaminen'
   AND rv.nimi = 'Rahavaraus E - Pysäkkikatokset';


INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE tehtava.nimi = 'Meluesteiden pienten vaurioiden korjaaminen'
   AND rv.nimi = 'Rahavaraus F - Meluesteet';


INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE tehtava.nimi = 'Juurakkopuhdistamo, selkeytys- ja hulevesiallas sekä -painanne'
   AND rv.nimi = 'Rahavaraus G - Juurakkopuhdistamo';


INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE tehtava.nimi = 'Aitojen vaurioiden korjaukset'
   AND rv.nimi = 'Rahavaraus H - Aidat';


INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE tehtava.nimi = 'Siltakeilojen sidekiveysten purkaumien, suojaverkkojen ja kosketussuojaseinien pienet korjaukset'
   AND rv.nimi = 'Rahavaraus I - Sillat ja laiturit';


INSERT INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rv.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus rv,
       tehtava
 WHERE tehtava.nimi = 'Tunnelien pienet korjaustyöt ja niiden liikennejärjestelyt'
   AND rv.nimi = 'Rahavaraus J - Tunnelien pienet korjaukset';

-- Tilaajan rahavaraus K
INSERT
  INTO rahavaraus_tehtava (rahavaraus_id, tehtava_id, luoja)
SELECT rahavaraus.id,
       tehtava.id,
       (SELECT id FROM kayttaja WHERE kayttajanimi = 'Integraatio')
  FROM rahavaraus,
       tehtava
 WHERE yksiloiva_tunniste = '794c7fbf-86b0-4f3e-9371-fb350257eb30'
   AND rahavaraus.nimi = 'Rahavaraus K';
