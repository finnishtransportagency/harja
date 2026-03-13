-- name: luo-kayttaja<!
-- single?: true
-- Luo uuden käyttäjän
INSERT
  INTO kayttaja
  (kayttajanimi, etunimi, sukunimi, sahkoposti, puhelin, organisaatio, luotu)
VALUES (:kayttajanimi, :etunimi, :sukunimi, :sahkoposti, :puhelin, :organisaatio, NOW())
RETURNING id;

-- name: paivita-kayttaja!
-- Päivittää käyttäjän tiedot
UPDATE kayttaja
   SET kayttajanimi = :kayttajanimi,
       etunimi = :etunimi,
       sukunimi = :sukunimi,
       sahkoposti = :sahkoposti,
       puhelin = :puhelin,
       organisaatio = :organisaatio,
       muokattu = NOW()
 WHERE id = :id;

-- name: piilota-jvh-nimi!
-- Päivittää käyttäjän tietoihin, että hänen nimi piilotetaan käyttöliittymässä. Tämä liittyy
-- jvh käyttäjiin, kun he joutuvat joskus muokkaamaan urakan tietoja, niin niputetaan kaikki jvh käyttäjät yhdeksi.
UPDATE kayttaja
SET piilota_nimi = TRUE,
    muokattu = NOW()
WHERE id = :id;

-- name: hae-ely-numerolla
-- Hakee ELY-keskuksen organisaation ELY numeron perusteella
SELECT id,nimi,tyyppi FROM organisaatio
 WHERE tyyppi = 'hallintayksikko' AND elynumero = :elynumero;

-- name: hae-elinvoimakeskus-numerolla
-- Hakee elinvoimakeskuksen organisaation elinvoimakeskusnumeron perusteella
SELECT id,nimi,tyyppi FROM organisaatio
WHERE tyyppi = 'elinvoimakeskus' AND elinvoimakeskusnumero = :elinvoimakeskusnumero;

-- name: hae-elinvoimakeskus-lyhenteella-tielupaa-varten
-- Hakee Elinvoimakeskuksen organisaation numeron perusteella
SELECT id,nimi FROM organisaatio
WHERE lyhenne = :elinvoimakeskuslyhenne and tyyppi = 'elinvoimakeskus';

-- name: hae-ely-numerolla-tielupaa-varten
-- Hakee ELY-keskuksen organisaation ELY numeron perusteella
-- Tielupasanomissa tulee enemmän Ely-arvoja kuin mitä Harjassa muuten käytetään.
SELECT id,nimi,tyyppi FROM organisaatio
 WHERE tyyppi in ('hallintayksikko', 'hallintayksikko-tilu') AND elynumero = :elynumero;


-- name: hae-organisaation-urakat
-- Palauttaa organisaation (hallintayksikkö tai urakoitsija) omien urakoiden id:t
SELECT u.id
  FROM urakka u
 WHERE u.urakoitsija = :org OR u.hallintayksikko = :org OR u.elinvoimakeskus_id = :org;


-- name: hae-kayttaja
-- Hakee yhden käyttäjän id:llä
SELECT
  k.id,
  k.kayttajanimi,
  k.etunimi,
  k.sukunimi,
  k.sahkoposti,
  k.puhelin,
  o.id           AS org_id,
  o.nimi         AS org_nimi,
  o.tyyppi       AS org_tyyppi,
  (SELECT array_agg(rooli)
     FROM kayttaja_rooli
    WHERE kayttaja = k.id
      AND poistettu = FALSE) AS roolit,
  k.jarjestelma
FROM kayttaja k
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
WHERE k.poistettu = FALSE
      AND k.id = :id;

-- name: hae-organisaatio-nimella
-- Hakee nimetyn organisaation. Tämä kysely on FIM käyttäjän tietojen yhdistämistä varten.
-- Ei tee käyttäjätarkistusta.
SELECT
  o.id     AS id,
  o.nimi   AS nimi,
  o.tyyppi AS tyyppi
FROM organisaatio o
WHERE lower(o.nimi) = lower(:nimi);

-- name: hae-organisaatio-idlla
-- Hakee organisaation id:n, nimen ja tyypin id:n perusteella.
SELECT id,nimi,tyyppi FROM organisaatio WHERE id = :id;

-- name: hae-organisaatio-y-tunnuksella
-- Hakee organisaation id:n, nimen ja tyypin Y-tunnuksen perusteella.
SELECT id,nimi,tyyppi FROM organisaatio WHERE ytunnus = :y-tunnus;

-- name: hae-kayttajien-tunnistetiedot
-- Hakee käyttäjistä ydintiedot tekstihaulla.
SELECT
  k.id,
  k.etunimi,
  k.sukunimi,
  k.jarjestelma AS jarjestelmasta,
  o.id     AS org_id,
  o.nimi   AS org_nimi,
  o.tyyppi AS org_tyyppi
FROM kayttaja k LEFT JOIN organisaatio o ON k.organisaatio = o.id
WHERE (k.kayttajanimi ILIKE :hakutermi
       OR k.etunimi ILIKE  :hakutermi
       OR k.sukunimi ILIKE  :hakutermi
      OR (CONCAT(k.etunimi, ' ' , k.sukunimi) ILIKE :hakutermi))
  AND (:organisaatiotyyppi::organisaatiotyyppi != 'urakoitsija'::organisaatiotyyppi
       OR k.organisaatio = :organisaatioid
       OR k.organisaatio IN (SELECT id FROM organisaatio WHERE tyyppi != 'urakoitsija')) -- urakoitsijalle ei kerrota toisten urakoitsijoiden henkilötietoja
      AND k.poistettu IS NOT TRUE
      AND k.jarjestelma IS NOT TRUE -- ei paljasteta järjestelmäkäyttäjien käyttäjätunnuksia
LIMIT 11;

-- name: hae-kayttaja-kayttajanimella
-- Hakee käyttäjän käyttäjänimellä
SELECT
  k.id,
  k.kayttajanimi,
  k.etunimi,
  k.sukunimi,
  k.sahkoposti,
  k.puhelin,
  o.id           AS org_id,
  o.nimi         AS org_nimi,
  o.tyyppi       AS org_tyyppi,
  (SELECT array_agg(rooli)
   FROM kayttaja_rooli
   WHERE kayttaja = k.id
     AND poistettu = FALSE) AS roolit,
  k.jarjestelma,
  piilota_nimi
FROM kayttaja k
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
WHERE k.poistettu = FALSE
      AND k.kayttajanimi = :kayttajanimi;

-- name: onko-kayttaja-urakan-organisaatiossa
-- Tarkistaa onko käyttäjä urakan urakoitsijaorganisaation jäsen
SELECT exists(
    SELECT u.id
    FROM urakka u
      JOIN kayttaja k ON k.organisaatio = u.urakoitsija
    WHERE u.id = :urakka_id AND
          k.id = :kayttaja_id);

-- name: onko-kayttajalla-lisaoikeus-urakkaan
-- Tarkistaa onko käyttäjälle annettu lisäoikeudet urakkaan
SELECT EXISTS(SELECT klu.id
                FROM kayttajan_lisaoikeudet_urakkaan klu
                         JOIN kayttaja k ON klu.kayttaja = k.id
               WHERE urakka = :urakka
                 AND klu.kayttaja = :kayttaja
                 AND k.poistettu IS NOT TRUE
                 AND klu.poistettu IS NOT TRUE);

-- name: onko-normikayttajalla-lisaoikeus-urakkaan
-- Tarkistaa onko käyttäjälle annettu lisäoikeudet urakkaan. Sallii myös käyttäjät, joilla ei ole järjestelmäasetuksia eli API käyttöoikeuksia annettu
SELECT EXISTS(SELECT klu.id
                FROM kayttajan_lisaoikeudet_urakkaan klu
                         JOIN kayttaja k ON klu.kayttaja = k.id
               WHERE urakka = :urakka
                 AND klu.kayttaja = :kayttaja
                 --AND k.jarjestelma IS TRUE
                 AND k.poistettu IS NOT TRUE
                 AND klu.poistettu IS NOT TRUE);

-- name: jarjestelmakysely-poista-urakan-kayttajien-lisaoikeudet!
-- Poista tietyn urakan kaikilta käyttäjiltä lisäoikeus urakkaan.
-- Tätä kutsutaan, kun urakat ovat päättyneet ja halutaan poistaa kaikki lisäoikeudet, jotta käyttäjillä ei enää ole oikeuksia vanhoihin urakoihin.
UPDATE kayttajan_lisaoikeudet_urakkaan
   SET poistettu = TRUE,
       muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio'),
       muokattu = NOW()
 WHERE urakka = :urakkaid;


-- name: onko-kayttaja-organisaatiossa
-- Tarkistaa onko käyttäjä organisaatiossa
SELECT exists(
    SELECT o.id
    FROM organisaatio o
      JOIN kayttaja k ON k.organisaatio = o.id
      AND o.ytunnus = :ytunnus
      AND k.id = :kayttaja_id);

-- name: onko-kayttaja-nimella-urakan-organisaatiossa
(SELECT EXISTS(SELECT id
               FROM kayttaja
               WHERE organisaatio = (SELECT urakoitsija
                                     FROM urakka
                                     WHERE id = :urakka)
                     AND etunimi = :etunimi
                     AND sukunimi = :sukunimi
                     AND (:puhelin::TEXT IS NULL OR puhelin = :puhelin)));


-- name: hae-urakan-id-sampo-idlla
-- single?: true
-- Hae urakan id Sampo ID:llä, sähke oikeuksien hakua varten
SELECT id FROM urakka WHERE sampoid = :sampoid;

-- name: hae-urakoitsijan-id-ytunnuksella
-- single?: true
SELECT id
  FROM organisaatio
 WHERE tyyppi = 'urakoitsija'
   AND ytunnus = :ytunnus;

-- name: hae-kayttajan-yleisin-urakkatyyppi
-- single?: true
SELECT tyyppi FROM urakka WHERE id IN (:idt) GROUP BY tyyppi ORDER BY count(id) DESC LIMIT 1;

-- name: onko-jarjestelma?
-- single?: true
SELECT jarjestelma
FROM kayttaja
WHERE kayttajanimi = :kayttajanimi;

-- name: onko-jarjestelma-ja-api-oikeus?
-- single?: true
SELECT jarjestelma
FROM kayttaja 
WHERE 
(  -- Jos käyttäjällä on 'kirjoitus' oikeus, luetaan se myös lukuoikeutena 
  (:api-oikeus = 'luku' AND 'kirjoitus'::apioikeus = ANY(api_oikeudet)) 
  OR 
  (:api-oikeus::apioikeus = ANY(api_oikeudet))
)
AND kayttajanimi = :kayttajanimi;

-- name: liikenneviraston-jarjestelma?
-- single?: true
SELECT exists(
    SELECT k.id
    FROM kayttaja k
      JOIN organisaatio o ON k.organisaatio = o.id
    WHERE k.kayttajanimi = :kayttajanimi AND
          k.jarjestelma IS TRUE AND
          o.tyyppi = 'liikennevirasto');

-- name: hae-yhteydenpidon-vastaanottajat
SELECT
  DISTINCT
  etunimi,
  sukunimi,
  sahkoposti
 FROM kayttaja
WHERE sahkoposti IS NOT NULL
  AND muokattu >  now() - interval '1 year' ;
