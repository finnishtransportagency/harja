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
WHERE (
              nimi = 'Vakiokokoisten liikennemerkkien uusiminen,  pelkkä merkki'
              OR nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)'
              OR nimi = 'Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)'
              OR nimi = 'Opastustaulun/-viitan uusiminen'
              OR nimi =
                 'Opastustaulujen ja liikennemerkkien rakentaminen tukirakenteineen (sis. liikennemerkkien poistamisia)'
              OR nimi = 'Opastustaulujen ja opastusviittojen uusiminen portaaliin'
              OR nimi = 'Töherrysten poisto'
              OR nimi = 'Töherrysten estokäsittely'
              OR nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet'
              OR nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet'
              OR nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet'
              OR nimi = 'Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet'
              OR nimi = 'Kannukaatosaumaus'
              OR nimi = 'KT-valuasfalttisaumaus'
              OR nimi = 'Päällystettyjen teiden palteiden poisto'
              OR nimi = 'Reunapalteen poisto kaiteen alta'
              OR nimi = 'Maakivien (>1m3) poisto'
              OR nimi = 'Avo-ojitus/päällystetyt tiet'
              OR nimi = 'Avo-ojitus/soratiet'
              OR nimi = 'Avo-ojitus/soratiet (kaapeli kaivualueella)'
              OR nimi = 'Kalliokynsien louhinta ojituksen yhteydessä'
              OR nimi = 'Soratien runkokelirikkokorjaukset'
          );

-- Näitä löytyy samalla nimellä useampia, niin tarvitaan tarkemat hakuehdot
UPDATE toimenpidekoodi
SET "raportoi-tehtava?" = TRUE
WHERE (
        nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm'
        OR nimi = 'Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm'
        OR nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm'
        OR nimi = 'Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm'
        OR nimi = 'Sillan päällysteen halkeaman avarrussaumaus'
        OR nimi = 'Sillan kannen päällysteen päätysauman korjaukset'
        OR nimi = 'Reunapalkin ja päällysteen väl. sauman tiivistäminen'
        OR nimi = 'Reunapalkin liikuntasauman tiivistäminen'
        OR nimi = 'Sillan päällysteen halkeaman avarrussaumaus'
        OR nimi = 'Sillan kannen päällysteen päätysauman korjaukset'
        OR nimi = 'Reunapalkin ja päällysteen väl. sauman tiivistäminen'
        OR nimi = 'Reunapalkin liikuntasauman tiivistäminen'
        OR nimi = 'Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)'
        OR nimi = 'Laskuojat/päällystetyt tiet'
        OR nimi = 'Laskuojat/soratiet'
    )
  AND tehtavaryhma is not null
  AND suunnitteluyksikko = 'jm';

-- Näitä löytyy samalla nimellä useampia, niin tarvitaan tarkemat hakuehdot
UPDATE toimenpidekoodi
SET "raportoi-tehtava?" = TRUE
WHERE (
        nimi = 'Pysäkkikatoksen uusiminen'
        OR nimi = 'Pysäkkikatoksen poistaminen'
    )
  AND tehtavaryhma is not null;