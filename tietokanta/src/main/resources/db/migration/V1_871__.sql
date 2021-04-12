-- Vain osa toimenpidekooditaulun tehtävistä otetaan VEMTR- ja tehtämääräraporteilla mukaan
-- Jotta tämä saadaan mahdollistettua, meidän pitää lisätä toimenpidekoodi tauluun merkintä,
-- jossa yksiselitteisesti määritellään nämä tehtävät.
-- Mukaanotettavat tehtävät on määritelty erillisessä excelissä.

ALTER TABLE toimenpidekoodi
    ADD "raportoi-tehtava?" BOOLEAN DEFAULT FALSE NOT NULL;

-- Päivitetään raportoi-tehtava? = true, niiltä tehtäviltä, jotka halutaan noihin raportteihin
-- Joudumme päivittämään nämä nimen perusteella, koska id ei täsmää eri ympäristöissä
UPDATE toimenpidekoodi
SET "raportoi-tehtava?" = TRUE
WHERE nimi IN ('Vakiokokoisten liikennemerkkien uusiminen,  pelkkä merkki',
               'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)',
              'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)',
              'Opastustaulun/-viitan uusiminen',
              'Opastustaulujen ja liikennemerkkien rakentaminen tukirakenteineen (sis. liikennemerkkien poistamisia)',
              'Opastustaulujen ja opastusviittojen uusiminen portaaliin',
              'Töherrysten poisto',
              'Töherrysten estokäsittely',
              'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet',
              'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet',
              'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet',
              'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet',
              'Kannukaatosaumaus',
              'KT-valuasfalttisaumaus',
              'Päällystettyjen teiden palteiden poisto',
              'Reunapalteen poisto kaiteen alta',
              'Maakivien (>1m3) poisto',
              'Avo-ojitus/päällystetyt tiet',
              'Avo-ojitus/soratiet',
              'Avo-ojitus/soratiet (kaapeli kaivualueella)',
              'Kalliokynsien louhinta ojituksen yhteydessä',
              'Soratien runkokelirikkokorjaukset'
          );

-- Näitä löytyy samalla nimellä useampia, niin tarvitaan tarkemat hakuehdot
UPDATE toimenpidekoodi
SET "raportoi-tehtava?" = TRUE
WHERE nimi IN ('Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm',
        'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm',
        'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm',
        'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm',
        'Sillan päällysteen halkeaman avarrussaumaus',
        'Sillan kannen päällysteen päätysauman korjaukset',
        'Reunapalkin ja päällysteen väl. sauman tiivistäminen',
        'Reunapalkin liikuntasauman tiivistäminen',
        'Sillan päällysteen halkeaman avarrussaumaus',
        'Sillan kannen päällysteen päätysauman korjaukset',
        'Reunapalkin ja päällysteen väl. sauman tiivistäminen',
        'Reunapalkin liikuntasauman tiivistäminen',
        'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)',
        'Laskuojat/päällystetyt tiet',
        'Laskuojat/soratiet')
  AND tehtavaryhma is not null
  AND suunnitteluyksikko = 'jm';

-- Näitä löytyy samalla nimellä useampia, niin tarvitaan tarkemat hakuehdot
UPDATE toimenpidekoodi
SET "raportoi-tehtava?" = TRUE
WHERE ( nimi = 'Pysäkkikatoksen uusiminen' OR nimi = 'Pysäkkikatoksen poistaminen')
  AND tehtavaryhma is not null;
