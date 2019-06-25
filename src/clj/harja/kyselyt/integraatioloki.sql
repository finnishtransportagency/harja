-- name: luo-integraatiotapahtuma<!
-- Luo uuden hankkeen.
INSERT INTO integraatiotapahtuma (integraatio, alkanut, ulkoinenid)
VALUES ((SELECT id
         FROM integraatio
         WHERE jarjestelma = :jarjestelma AND nimi = :nimi),
        current_timestamp,
        :ulkoinenid);

-- name: merkitse-integraatiotapahtuma-paattyneeksi!
-- Paivittaa hankkeen Samposta saaduilla tiedoilla
UPDATE integraatiotapahtuma
SET paattynyt = current_timestamp, onnistunut = :onnistunut, lisatietoja = :lisatietoja
WHERE id = :id;

-- name: merkitse-integraatiotapahtuma-paattyneeksi-ulkoisella-idlla<!
-- Paivittaa hankkeen Samposta saaduilla tiedoilla
UPDATE integraatiotapahtuma
SET paattynyt = current_timestamp, onnistunut = :onnistunut, lisatietoja = :lisatietoja
WHERE ulkoinenid = :ulkoinen_id;

-- name: aseta-ulkoinen-id-integraatiotapahtumalle!
-- Asettaa ulkoisen id:n integraatiotapahtumalle
UPDATE integraatiotapahtuma
SET ulkoinenid = :ulkoinenid
WHERE id = :id;

-- name: luo-integraatioviesti<!
-- Luo uuden integraatioviestin
INSERT INTO integraatioviesti (integraatiotapahtuma, osoite, suunta, sisaltotyyppi, siirtotyyppi,
                               sisalto, otsikko, parametrit, kasitteleva_palvelin)
VALUES
  (:integraatiotapahtuma, :osoite, :suunta :: INTEGRAATIOSUUNTA, :sisaltotyyppi, :siirtotyyppi,
   :sisalto, :otsikko, :parametrit, :kasittelevapalvelin);

-- name: hae-tapahtumaid-ulkoisella-idlla
-- Hakee tapahtuma id:n ulkoisella id:llä ja integraatiolla
SELECT id
FROM integraatiotapahtuma
WHERE ulkoinenid = :ulkoinenid AND
      integraatio = (SELECT id
                     FROM integraatio
                     WHERE jarjestelma = :jarjestelma AND nimi = :nimi);

-- name: poista-ennen-paivamaaraa-kirjatut-tapahtumat!
-- Poistaa ennen annettua päivämäärää kirjatut integraatiotapahtumat viesteineen
WITH
    poistettavat_tapahtumaidt AS (
      SELECT id
      FROM integraatiotapahtuma
      WHERE alkanut < :paivamaara AND integraatio IN
                                      (SELECT id
                                       FROM integraatio
                                       WHERE :jarjestelma :: VARCHAR IS NULL OR jarjestelma = :jarjestelma)),
    viestien_poisto AS (
    DELETE FROM integraatioviesti
    WHERE integraatiotapahtuma IN (
      SELECT id
      FROM poistettavat_tapahtumaidt))
DELETE FROM integraatiotapahtuma
WHERE id IN (SELECT id
             FROM poistettavat_tapahtumaidt);

-- name: hae-jarjestelmien-integraatiot
-- Hakee kaikki järjestelmien integraatiot
SELECT
  jarjestelma AS jarjestelma,
  nimi        AS integraatio
FROM integraatio;

-- name: hae-jarjestelman-integraatiotapahtumat-aikavalilla
-- Hakee annetun järjestelmän integraatiotapahtumat annetulla aikavälillä
SELECT DISTINCT ON (it.id)
  it.id,
  it.ulkoinenid,
  it.lisatietoja,
  it.alkanut,
  it.paattynyt,
  it.onnistunut,
  i.id          AS integraatio_id,
  i.jarjestelma AS integraatio_jarjestelma,
  i.nimi        AS integraatio_nimi
FROM integraatiotapahtuma it
  JOIN integraatio i ON it.integraatio = i.id
  LEFT JOIN integraatioviesti iv ON it.id = iv.integraatiotapahtuma
WHERE (:jarjestelma :: VARCHAR IS NULL OR i.jarjestelma = :jarjestelma) AND
      (:integraatio :: VARCHAR IS NULL OR i.nimi = :integraatio) AND
      (:onnistunut :: BOOLEAN IS NULL OR it.onnistunut = :onnistunut) AND
      ((:alkaen :: TIMESTAMP IS NULL AND alkanut >= current_date) OR alkanut >= :alkaen) AND
      (:paattyen :: TIMESTAMP IS NULL OR alkanut <= :paattyen) AND
      (:otsikot :: TEXT IS NULL OR iv.otsikko ILIKE '%' || :otsikot || '%') AND
      (:parametrit :: TEXT IS NULL OR iv.parametrit ILIKE '%' || :parametrit || '%') AND
      (:sisalto :: TEXT IS NULL OR iv.sisalto ILIKE '%' || :sisalto || '%') AND
      (:kesto :: INTEGER IS NULL OR :kesto < EXTRACT(EPOCH FROM (paattynyt - alkanut )))
ORDER BY it.id DESC, it.alkanut DESC LIMIT :limit;


-- name: hae-integraatiotapahtuman-viestit
-- Hakee annetun integraatiotapahtuman viestit
SELECT
  id,
  integraatiotapahtuma,
  suunta,
  sisaltotyyppi,
  siirtotyyppi,
  sisalto,
  otsikko,
  parametrit,
  osoite,
  kasitteleva_palvelin AS "kasitteleva-palvelin"
FROM integraatioviesti
WHERE integraatiotapahtuma = :integraatiotapahtumaid;

-- name: hae-integraatiotapahtumien-maarat
-- Hakee annetun integraation tapahtumien määrät päivittäin ryhmiteltynä
SELECT
  date_trunc('day', it.alkanut) AS pvm,
  it.integraatio                AS integraatio,
  i.jarjestelma                 AS jarjestelma,
  i.nimi                        AS nimi,
  count(*)                      AS maara
FROM integraatiotapahtuma it
  JOIN integraatio i ON it.integraatio = i.id
WHERE (:jarjestelma_annettu = FALSE OR i.jarjestelma ILIKE :jarjestelma)
      AND (:integraatio_annettu = FALSE OR i.nimi ILIKE :integraatio)
GROUP BY pvm, integraatio, jarjestelma, nimi
ORDER BY pvm;

-- name: hae-integraation-id
SELECT id
FROM integraatio
WHERE jarjestelma = :jarjestelma AND nimi = :integraatio;

-- name: hae-uusin-integraatiotapahtuma-id
-- single?: true
SELECT it.id
FROM integraatiotapahtuma it
  JOIN integraatio i ON i.id = it.integraatio
WHERE i.jarjestelma = :jarjestelma AND
      i.nimi = :nimi AND
      it.paattynyt IS NULL AND
      it.alkanut :: DATE = CURRENT_DATE
ORDER BY it.alkanut DESC
LIMIT 1;
