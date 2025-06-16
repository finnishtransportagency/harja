-- Lisätään uusi sarake, joka määrittää onko tehtävän valinta pakollinen uutta kulua luotaessa
ALTER TABLE tehtava 
ADD COLUMN pakollinen_uudessa_kulussa BOOLEAN DEFAULT FALSE;

-- Lisätään uusi sarake tehtavalle kohdistus-tauluun, joka viittaa tehtävään
ALTER TABLE kulu_kohdistus 
ADD COLUMN tehtava INTEGER REFERENCES tehtava(id);

-- Päivitetään puuttuva tehtäväryhmä 
UPDATE tehtava
SET 
    tehtavaryhma 		= (select id from tehtavaryhma where nimi = 'O - Sorapientareet' AND yksiloiva_tunniste = 'f51c3d67-d21f-4286-bbb5-9354dcd073d6'),
    muokattu            = current_timestamp,
    muokkaaja           = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Päällystettyjen teiden palteiden poisto';

-- Päivitetään puuttuva tehtäväryhmä STG
UPDATE tehtava
SET 
    tehtavaryhma 		= (select id from tehtavaryhma where nimi = 'D - Kesäsuola, materiaali' AND yksiloiva_tunniste = '0f5cd978-6304-414b-8082-bfbd7eb88c0e'),
    muokattu            = current_timestamp,
    muokkaaja           = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Kesäsuola (CaCl2, materiaali)';


-- Päivitetään puuttuva tehtäväryhmä STG
UPDATE tehtava
SET 
    tehtavaryhma 		= (select id from tehtavaryhma where nimi = 'D - M - Liikenteen varmistaminen kelirikkokohteessa' AND yksiloiva_tunniste = 'd77048cb-0fee-454d-9af0-e9656fcb7044'),
    muokattu            = current_timestamp,
    muokkaaja           = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Liikenteen varmistaminen kelirikkokohteessa (tonni)';

-- Migraatio pakollisten tehtavien asettamiseksi
WITH pakolliset_tehtavat (nimi) AS (
    VALUES
    ('Suolaus'),
    ('Kalium- tai natriumformiaatin käyttö liukkaudentorjuntaan (materiaali)'),
    ('Liukkaudentorjunta hiekoituksella (materiaali)'), -- TODO: Lisää uusi tehtävä ('Ennalta arvaamattomien kuljetusten avustaminen hiekoituksella (materiaali)),
    ('Vakiokokoisten liikennemerkkien uusiminen,  pelkkä merkki'),
    ('Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (60 mm varsi)'),
    ('Vakiokokoisten liikennemerkkien uusiminen ja lisääminen merkki tukirakenteineen (90 mm varsi)'),
    ('Opastustaulun/-viitan uusiminen'),
    ('Opastustaulun/-viitan uusiminen tukirakenteineen (sis. liikennemerkkien poistamisia)'),
    ('Opastustaulujen ja opastusviittojen uusiminen portaaliin'),
    ('Töherrysten poisto'),
    ('Töherrysten estokäsittely'),
    ('Runkopuiden poisto'),
    ('Reunantäyttö'),
    ('Päällystettyjen teiden palteiden poisto'),
    ('Reunapalteen poisto kaiteen alta'),
    ('Maakivien (>1m3) poisto'),
    ('Kesäsuola (CaCl2, materiaali)'),
    ('Sorastus'),
    ('Liikenteen varmistaminen kelirikkokohteessa (tonni)'),
    ('AB-paikkaus levittäjällä'),
    ('PAB-paikkaus levittäjällä'),
    ('PAB-paikkaus käsin'),
    ('KT-valuasfalttipaikkaus K'),
    ('KT-valuasfalttipaikkaus T'),
    ('KT-reikävaluasfalttipaikkaus'),
    ('Käsin tehtävät paikkaukset pikapaikkausmassalla'),
    ('Sirotepuhalluspaikkaus (SIPU)'),
    ('Kannukaatosaumaus'),
    ('KT-valuasfalttisaumaus'),
    ('Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, päällystetyt tiet'),
    ('Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, päällystetyt tiet'),
    ('Päällystetyn tien rumpujen korjaus ja uusiminen Ø <= 600 mm'),
    ('Päällystetyn tien rumpujen korjaus ja uusiminen  Ø> 600  <= 800 mm'), 
    ('Yksityisten rumpujen korjaus ja uusiminen  Ø ≤ 400 mm, soratiet'),
    ('Yksityisten rumpujen korjaus ja uusiminen  Ø > 400 mm ≤ 600 mm, soratiet'),
    ('Soratien rumpujen korjaus ja uusiminen  Ø <= 600 mm'),
    ('Soratien rumpujen korjaus ja uusiminen  Ø> 600  <=800 mm'),
    ('Avo-ojitus/päällystetyt tiet'),
    ('Avo-ojitus/päällystetyt tiet (kaapeli kaivualueella)'),
    ('Laskuojat/päällystetyt tiet'),
    ('Avo-ojitus/soratiet'),
    ('Avo-ojitus/soratiet (kaapeli kaivualueella)'),
    ('Sorateitä kaventava ojitus'),
    ('Laskuojat/soratiet'), 
    ('Kalliokynsien louhinta ojituksen yhteydessä'),
    ('Kaiteiden poisto ja uusiminen'),
    ('Kaiteiden kunnostaminen'),
    ('Kaiteiden rakentaminen'),
    ('Reunapaalujen uusiminen'),
    ('Nopeusnäyttötaulun hankinta'),
    ('Pysäkkikatoksen uusiminen'),
    ('Pysäkkikatoksen poistaminen')
)
UPDATE tehtava t
SET pakollinen_uudessa_kulussa = true,
    muokattu            = current_timestamp,
    muokkaaja           = (select id from kayttaja where kayttajanimi = 'Integraatio')
FROM pakolliset_tehtavat vt
WHERE vt.nimi = t.nimi
  AND t.tehtavaryhma IS NOT NULL;