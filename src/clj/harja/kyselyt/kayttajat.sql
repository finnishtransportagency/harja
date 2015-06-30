-- name: hae-kirjautumistiedot
-- Hakee annetulle KOKA käyttäjätunnukselle kirjautumistiedot
SELECT k.id, k.kayttajanimi, k.etunimi, k.sukunimi, k.sahkoposti, k.puhelin,
       o.id as org_id, o.nimi as org_nimi, o.tyyppi as org_tyyppi
  FROM kayttaja k LEFT JOIN organisaatio o ON k.organisaatio = o.id
 WHERE k.kayttajanimi = :koka
   AND k.poistettu = false

-- name: hae-kayttajat
-- Hakee käyttäjiä käyttäjähallinnan listausta varten.
-- Haun suorittava käyttäjä annetaan parametrina ja vain käyttäjät, jotka hän saa nähdä palautetaan.
SELECT k.id, k.kayttajanimi, k.etunimi, k.sukunimi, k.sahkoposti, k.puhelin,
       o.id as org_id, o.nimi as org_nimi, o.tyyppi as org_tyyppi,
       array_cat((SELECT array_agg(rooli) FROM kayttaja_rooli WHERE kayttaja = k.id AND poistettu=false),
                 (SELECT array_agg(rooli) FROM kayttaja_urakka_rooli WHERE kayttaja = k.id AND poistettu=false)) as roolit
  FROM kayttaja k
       LEFT JOIN organisaatio o ON k.organisaatio = o.id
 WHERE k.poistettu = false
   AND
       -- tarkistetaan käyttöoikeus: pääkäyttäjä näkee kaikki, muuten oman organisaation
       ((SELECT COUNT(*) FROM kayttaja_rooli WHERE kayttaja=:hakija AND rooli='jarjestelmavastuuhenkilo' AND poistettu=false) > 0
        OR
	k.organisaatio IN (SELECT kor.organisaatio FROM kayttaja_organisaatio_rooli kor
	                    WHERE kor.kayttaja = :hakija
			      AND kor.rooli = 'urakoitsijan paakayttaja'
			      ))
       -- tarkistetaan hakuehto
   AND (:haku = '' OR (k.kayttajanimi LIKE :haku OR k.etunimi LIKE :haku OR k.sukunimi LIKE :haku))
OFFSET :alku
 LIMIT :maara

-- name: hae-kayttajat-lkm
-- Hakee lukumäärän käyttäjälukumäärälle, jonka hae-kayttajat palauttaisi ilman LIMIT/OFFSET määritystä.
SELECT COUNT(k.id) as lkm
  FROM kayttaja k
 WHERE k.poistettu = false
   AND
       -- tarkistetaan käyttöoikeus: pääkäyttäjä näkee kaikki, muuten oman organisaation
       ((SELECT COUNT(*) FROM kayttaja_rooli WHERE kayttaja=:hakija AND rooli='jarjestelmavastuuhenkilo' AND poistettu=false) > 0
        OR
	k.organisaatio IN (SELECT kor.organisaatio FROM kayttaja_organisaatio_rooli kor
	                    WHERE kor.kayttaja = :hakija
			      AND kor.rooli = 'urakoitsijan paakayttaja'
			      ))
       -- tarkistetaan hakuehto
   AND (:haku = '' OR (k.kayttajanimi LIKE :haku OR k.etunimi LIKE :haku OR k.sukunimi LIKE :haku))

-- name: hae-kayttaja
-- Hakee yhden käyttäjän id:llä
SELECT k.id, k.kayttajanimi, k.etunimi, k.sukunimi, k.sahkoposti, k.puhelin,
       o.id as org_id, o.nimi as org_nimi, o.tyyppi as org_tyyppi,
       array_cat((SELECT array_agg(rooli) FROM kayttaja_rooli WHERE kayttaja = k.id AND poistettu=false),
                 (SELECT array_agg(rooli) FROM kayttaja_urakka_rooli WHERE kayttaja = k.id AND poistettu=false)) as roolit
  FROM kayttaja k
       LEFT JOIN organisaatio o ON k.organisaatio = o.id
 WHERE k.poistettu = false
   AND k.id = :id

 
-- name: hae-kayttajan-urakka-roolit
-- Hakee käyttäjän urakka roolit.
SELECT rooli, urakka as urakka_id, luotu,
       ur.nimi as urakka_nimi,
       urk.nimi as urakka_urakoitsija_nimi, urk.id as urakka_urakoitsija_id,
       hal.nimi as urakka_hallintayksikko_nimi, hal.id as urakka_hallintayksikko_id
  FROM kayttaja_urakka_rooli
       LEFT JOIN urakka ur ON urakka=ur.id
       LEFT JOIN organisaatio urk ON ur.urakoitsija=urk.id
       LEFT JOIN organisaatio hal ON ur.hallintayksikko=hal.id
 WHERE kayttaja = :kayttaja AND poistettu = false

-- name: lisaa-urakka-rooli<!
-- Lisää annetulle käyttäjälle roolin urakkaan.
INSERT INTO kayttaja_urakka_rooli (luoja, luotu, kayttaja, urakka, rooli) VALUES (:luoja, NOW(), :kayttaja, :urakka, :rooli::kayttajarooli)

-- name: poista-urakka-rooli!
-- Poista käyttäjän rooli annetusta urakkasta.
UPDATE kayttaja_urakka_rooli SET poistettu=true, muokkaaja=:muokkaaja, muokattu=NOW() WHERE kayttaja=:kayttaja AND urakka=:urakka AND rooli=:rooli::kayttajarooli

-- name: hae-kayttajan-roolit
-- Palauttaa kaikki käyttäjän roolit (sekä urakka että tavalliset).
SELECT array_cat((SELECT array_agg(rooli) FROM kayttaja_rooli WHERE kayttaja = :kayttaja AND poistettu=false),
                 (SELECT array_agg(rooli) FROM kayttaja_urakka_rooli WHERE kayttaja = :kayttaja AND poistettu=false)) as roolit
		 

-- name: poista-rooli!
-- Poista käyttäjältä rooli.
UPDATE kayttaja_rooli SET poistettu=true, muokkaaja=:muokkaaja, muokattu=NOW() WHERE kayttaja=:kayttaja AND rooli=:rooli::kayttajarooli

-- name: poista-urakka-roolit!
-- Poista käyttäjältä urakka roolit.
UPDATE kayttaja_urakka_rooli SET poistettu=true, muokkaaja=:muokkaaja, muokattu=NOW()
 WHERE kayttaja=:kayttaja AND rooli=:rooli::kayttajarooli
 
-- name: lisaa-rooli<!
-- Lisää käyttäjälle rooli.
INSERT INTO kayttaja_rooli (luoja, luotu, kayttaja, rooli) VALUES (:luoja, NOW(), :kayttaja, :rooli::kayttajarooli)


-- name: poista-kayttaja!
-- Merkitsee annetun käyttäjän poistetuksi
UPDATE kayttaja SET poistettu=true,muokkaaja=:muokkaaja,muokattu=NOW() WHERE id=:kayttaja

-- name: hae-organisaatio-nimella
-- Hakee nimetyn organisaation. Tämä kysely on FIM käyttäjän tietojen yhdistämistä varten.
-- Ei tee käyttäjätarkistusta.
SELECT o.id as id, o.nimi as nimi, o.tyyppi as tyyppi
  FROM organisaatio o
 WHERE o.nimi = :nimi

-- name: hae-organisaatioita
-- Käyttäjän organisaatiohaku nimen osalla.
SELECT o.id as id, o.nimi as nimi, o.tyyppi as tyyppi
  FROM organisaatio o
 WHERE o.nimi ILIKE :haku


-- name: luo-kayttaja<!
-- Luo uuden käyttäjän FIM tietojen pohjalta
INSERT
  INTO kayttaja
       (kayttajanimi,etunimi,sukunimi,sahkoposti,puhelin,organisaatio)
VALUES (:kayttajanimi, :etunimi, :sukunimi, :sahkoposti, :puhelin, :organisaatio)

-- name: hae-kayttajien-tunnistetiedot
-- Hakee käyttäjistä ydintiedot tekstihaulla.
SELECT k.id, k.kayttajanimi, k.etunimi, k.sukunimi,
       o.id as org_id, o.nimi as org_nimi, o.tyyppi as org_tyyppi
  FROM kayttaja k LEFT JOIN organisaatio o ON k.organisaatio = o.id
 WHERE k.kayttajanimi ILIKE :koka
    OR o.nimi ILIKE :koka
   AND k.poistettu = false

-- name: hae-jarjestelmakayttajan-id-ytunnuksella
-- Hakee järjestelmäkäyttäjän järjestelmän ja organisaation y-tunnuksen avulla
SELECT k.id
FROM kayttaja k
  JOIN organisaatio o ON k.organisaatio = o.id
WHERE k.kayttajanimi = :jarjestelma AND
      k.jarjestelma = TRUE AND
      o.ytunnus = :ytunnus;