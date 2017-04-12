-- name: hae-harjassa-luodut-urakat
SELECT
  u.id,
  u.nimi,
  u.alue,
  u.alkupvm,
  u.loppupvm,
  u.tyyppi,
  u.sopimustyyppi,
  hal.id                                                        AS hallintayksikko_id,
  hal.nimi                                                      AS hallintayksikko_nimi,
  hal.lyhenne                                                   AS hallintayksikko_lyhenne,
  urk.id                                                        AS urakoitsija_id,
  urk.nimi                                                      AS urakoitsija_nimi,
  urk.ytunnus                                                   AS urakoitsija_ytunnus,
  s.nimi                                                        AS sopimus_nimi,
  s.id                                                          AS sopimus_id,
  s.paasopimus                                                  AS sopimus_paasopimus,
  s.alkupvm                                                     AS sopimus_alkupvm,
  s.loppupvm                                                    AS sopimus_loppupvm,
  h.nimi                                                        AS hanke_nimi,
  h.id                                                          AS hanke_id,
  h.alkupvm                                                     AS hanke_alkupvm,
  h.loppupvm                                                    AS hanke_loppupvm,
  ST_Simplify(au.alue, 50)                                      AS alueurakan_alue
FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
  LEFT JOIN alueurakka au ON u.urakkanro = au.alueurakkanro
  LEFT JOIN hanke h ON u.hanke = h.id
  LEFT JOIN sopimus s ON u.id = s.urakka
WHERE u.harjassa_luotu IS TRUE;

-- name: hae-lahimmat-urakat-aikavalilta
SELECT
  u.id,
  u.nimi,
  u.tyyppi,
  u.urakkanro,
  COALESCE(st_distance(u.alue, st_makepoint(:x, :y)),
  st_distance(au.alue, st_makepoint(:x, :y))) AS etaisyys
FROM urakka u
LEFT JOIN alueurakka au ON au.alueurakkanro = u.urakkanro
WHERE
-- Urakka on käynnissä
(u.alkupvm <= now() AND
u.loppupvm > now())
OR
-- Urakka on käynnissä (loppua ei tiedossa)
(u.alkupvm <= now() AND
u.loppupvm IS NULL)
OR
-- Urakan takuuaika on voimassa
(u.alkupvm <= now() AND
u.takuu_loppupvm > now())
ORDER BY etaisyys;

-- name: hae-kaikki-urakat-aikavalilla
SELECT
  u.id     AS urakka_id,
  u.nimi   AS urakka_nimi,
  u.tyyppi AS tyyppi,
  o.id     AS hallintayksikko_id,
  o.nimi   AS hallintayksikko_nimi
FROM urakka u
  JOIN organisaatio o ON u.hallintayksikko = o.id
WHERE ((u.loppupvm >= :alku AND u.alkupvm <= :loppu) OR (u.loppupvm IS NULL AND u.alkupvm <= :loppu)) AND
      (:urakoitsija :: INTEGER IS NULL OR :urakoitsija = u.urakoitsija) AND
      (:urakkatyyppi :: urakkatyyppi IS NULL OR u.tyyppi :: TEXT = :urakkatyyppi) AND
      (:hallintayksikko_annettu = FALSE OR u.hallintayksikko IN (:hallintayksikko));

-- name: hae-kaynnissa-olevat-urakat
SELECT
  u.id,
  u.nimi,
  u.tyyppi
FROM urakka u
WHERE (u.alkupvm IS NULL OR u.alkupvm <= current_date) AND
      (u.loppupvm IS NULL OR u.loppupvm >= current_date);

-- name: hae-kaynnissa-olevat-ja-tulevat-urakat
SELECT
  u.id,
  u.nimi,
  u.tyyppi,
  u.alkupvm,
  u.loppupvm
FROM urakka u
WHERE u.alkupvm >= current_date
      OR
      (u.alkupvm <= current_date AND
       u.loppupvm >= current_date);

-- name: hae-hallintayksikon-urakat
SELECT
  u.id,
  u.nimi,
  u.tyyppi
FROM urakka u
  JOIN organisaatio o ON o.id = u.hallintayksikko
WHERE o.id = :hy;

-- name: listaa-urakat-hallintayksikolle
-- Palauttaa listan annetun hallintayksikön (id) urakoista. Sisältää perustiedot ja geometriat.
SELECT
  u.id,
  u.nimi,
  u.sampoid,
  u.alue,
  u.alkupvm,
  u.loppupvm,
  u.tyyppi,
  u.sopimustyyppi,
  u.indeksi,
  hal.id                      AS hallintayksikko_id,
  hal.nimi                    AS hallintayksikko_nimi,
  hal.lyhenne                 AS hallintayksikko_lyhenne,
  urk.id                      AS urakoitsija_id,
  urk.nimi                    AS urakoitsija_nimi,
  urk.ytunnus                 AS urakoitsija_ytunnus,
  yt.yhatunnus                AS yha_yhatunnus,
  yt.yhaid                    AS yha_yhaid,
  yt.yhanimi                  AS yha_yhanimi,
  yt.elyt :: TEXT []          AS yha_elyt,
  yt.vuodet :: INTEGER []     AS yha_vuodet,
  yt.sidonta_lukittu          AS yha_sidonta_lukittu,
  yt.kohdeluettelo_paivitetty AS yha_kohdeluettelo_paivitetty,
  yt.kohdeluettelo_paivittaja AS yha_kohdeluettelo_paivittaja,
  k.etunimi                   AS yha_kohdeluettelo_paivittaja_etunimi,
  k.sukunimi                  AS yha_kohdeluettelo_paivittaja_sukunimi,
  u.takuu_loppupvm,
  (SELECT array_agg(concat((CASE WHEN paasopimus IS NULL
    THEN '*'
                            ELSE '' END),
                           id, '=', sampoid))
   FROM sopimus s
   WHERE urakka = u.id)       AS sopimukset,

  -- Urakka-alue: tällä hetkellä tuetaan joko hoidon alueurakan, teknisten laitteiden ja siltapalvelusopimusten alueita.
  CASE
  WHEN u.tyyppi = 'siltakorjaus' :: urakkatyyppi
    THEN ST_Simplify(sps.alue, 50)
  WHEN u.tyyppi = 'tekniset-laitteet' :: urakkatyyppi
    THEN ST_Simplify(tlu.alue, 50)
  WHEN (u.tyyppi = 'hoito' :: urakkatyyppi AND au.alue IS NOT NULL)
    THEN
      -- Luodaan yhtenäinen polygon alueurakan alueelle (multipolygonissa voi olla reikiä)
      hoidon_alueurakan_geometria(u.urakkanro)
  ELSE
    ST_Simplify(au.alue, 50)
  END                         AS alueurakan_alue

FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
  LEFT JOIN alueurakka au ON u.urakkanro = au.alueurakkanro
  LEFT JOIN tekniset_laitteet_urakka tlu ON u.urakkanro = tlu.urakkanro
  LEFT JOIN siltapalvelusopimus sps ON u.urakkanro = sps.urakkanro
  LEFT JOIN yhatiedot yt ON u.id = yt.urakka
  LEFT JOIN kayttaja k ON k.id = yt.kohdeluettelo_paivittaja
WHERE hallintayksikko = :hallintayksikko
      AND (u.id IN (:sallitut_urakat)
           OR (('hallintayksikko' :: organisaatiotyyppi = :kayttajan_org_tyyppi :: organisaatiotyyppi OR
                'liikennevirasto' :: organisaatiotyyppi = :kayttajan_org_tyyppi :: organisaatiotyyppi)
               OR ('urakoitsija' :: organisaatiotyyppi = :kayttajan_org_tyyppi :: organisaatiotyyppi AND
                   :kayttajan_org_id = urk.id)));

-- name: hae-urakan-organisaatio
-- Hakee urakan organisaation urakka-id:llä.
SELECT
  o.nimi,
  o.ytunnus
FROM organisaatio o
  JOIN urakka u ON o.id = u.urakoitsija
WHERE u.id = :urakka;

-- name: hae-urakoita
-- Hakee urakoita tekstihaulla.
SELECT
  u.id,
  u.nimi,
  u.sampoid,
  u.alue,
  u.alkupvm,
  u.loppupvm,
  u.tyyppi,
  u.sopimustyyppi,
  u.takuu_loppupvm,
  hal.id                   AS hallintayksikko_id,
  hal.nimi                 AS hallintayksikko_nimi,
  hal.lyhenne              AS hallintayksikko_lyhenne,
  urk.id                   AS urakoitsija_id,
  urk.nimi                 AS urakoitsija_nimi,
  urk.ytunnus              AS urakoitsija_ytunnus,
  (SELECT array_agg(concat(id, '=', sampoid))
   FROM sopimus s
   WHERE urakka = u.id)    AS sopimukset,
  ST_Simplify(au.alue, 50) AS alueurakan_alue
FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
  LEFT JOIN alueurakka au ON u.urakkanro = au.alueurakkanro
WHERE u.nimi ILIKE :teksti
      OR hal.nimi ILIKE :teksti
      OR urk.nimi ILIKE :teksti;

-- name: hae-organisaation-urakat
-- Hakee organisaation "omat" urakat, joko urakat joissa annettu hallintayksikko on tilaaja
-- tai urakat joissa annettu urakoitsija on urakoitsijana.
SELECT
  u.id,
  u.nimi,
  u.sampoid,
  u.alue,
  u.alkupvm,
  u.loppupvm,
  u.tyyppi,
  u.sopimustyyppi,
  u.takuu_loppupvm,
  hal.id                   AS hallintayksikko_id,
  hal.nimi                 AS hallintayksikko_nimi,
  hal.lyhenne              AS hallintayksikko_lyhenne,
  urk.id                   AS urakoitsija_id,
  urk.nimi                 AS urakoitsija_nimi,
  urk.ytunnus              AS urakoitsija_ytunnus,
  (SELECT array_agg(concat(id, '=', sampoid))
   FROM sopimus s
   WHERE urakka = u.id)    AS sopimukset,
  ST_Simplify(au.alue, 50) AS alueurakan_alue
FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
  LEFT JOIN alueurakka au ON u.urakkanro = au.alueurakkanro
WHERE urk.id = :organisaatio
      OR hal.id = :organisaatio;

-- name: tallenna-urakan-sopimustyyppi!
-- Tallentaa urakalle sopimustyypin
UPDATE urakka
SET sopimustyyppi = :sopimustyyppi :: sopimustyyppi
WHERE id = :urakka;

-- name: tallenna-urakan-tyyppi!
-- Vaihtaa urakan tyypin
UPDATE urakka
SET tyyppi = :urakkatyyppi :: urakkatyyppi
WHERE id = :urakka;

-- name: hae-urakan-sopimustyyppi
-- Hakee urakan sopimustyypin
SELECT sopimustyyppi
FROM urakka
WHERE id = :urakka;

-- name: hae-urakan-tyyppi
-- Hakee urakan tyypin
SELECT tyyppi
FROM urakka
WHERE id = :urakka;

-- name: hae-urakoiden-tunnistetiedot
-- Hakee urakoista ydintiedot tekstihaulla.
SELECT
  u.id,
  u.nimi,
  u.hallintayksikko,
  u.sampoid
FROM urakka u
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
WHERE (u.nimi ILIKE :teksti
       OR u.sampoid ILIKE :teksti)
      AND (('hallintayksikko' :: organisaatiotyyppi = :kayttajan_org_tyyppi :: organisaatiotyyppi OR
            'liikennevirasto' :: organisaatiotyyppi = :kayttajan_org_tyyppi :: organisaatiotyyppi)
           OR ('urakoitsija' :: organisaatiotyyppi = :kayttajan_org_tyyppi :: organisaatiotyyppi AND
               :kayttajan_org_id = urk.id))
LIMIT 11;

-- name: hae-urakka
-- Hakee urakan perustiedot id:llä APIa varten.
SELECT
  u.id,
  u.nimi,
  u.tyyppi,
  u.alkupvm,
  u.loppupvm,
  u.indeksi,
  u.takuu_loppupvm,
  u.urakkanro AS alueurakkanumero,
  urk.nimi    AS urakoitsija_nimi,
  urk.ytunnus AS urakoitsija_ytunnus
FROM urakka u
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
WHERE u.id = :id;

-- name: hae-urakoiden-organisaatiotiedot
-- Hakee joukolle urakoita urakan ja hallintayksikön nimet ja id:t
SELECT
  u.id     AS urakka_id,
  u.nimi   AS urakka_nimi,
  u.tyyppi AS tyyppi,
  hy.id    AS hallintayksikko_id,
  hy.nimi  AS hallintayksikko_nimi
FROM urakka u
  JOIN organisaatio hy ON u.hallintayksikko = hy.id
WHERE u.id IN (:id);

-- name: hae-urakat-ytunnuksella
SELECT
  u.id,
  u.nimi,
  u.tyyppi,
  u.alkupvm,
  u.loppupvm,
  u.takuu_loppupvm,
  u.urakkanro AS alueurakkanumero,
  urk.nimi    AS urakoitsija_nimi,
  urk.ytunnus AS urakoitsija_ytunnus
FROM urakka u
  JOIN organisaatio urk ON u.urakoitsija = urk.id
                           AND urk.ytunnus = :ytunnus;

-- name: hae-urakan-sopimukset
-- Hakee urakan sopimukset urakan id:llä.
SELECT
  s.id,
  s.nimi,
  s.alkupvm,
  s.loppupvm
FROM sopimus s
WHERE s.urakka = :urakka;

-- name: onko-olemassa
-- Tarkistaa onko id:n mukaista urakkaa olemassa tietokannassa
SELECT EXISTS(SELECT id
              FROM urakka
              WHERE id = :id);

-- name: paivita-hankkeen-tiedot-urakalle!
-- Päivittää hankkeen sampo id:n avulla urakalle
UPDATE urakka
SET hanke = (SELECT id
             FROM hanke
             WHERE sampoid = :hanke_sampo_id)
WHERE hanke_sampoid = :hanke_sampo_id;

-- name: luo-urakka<!
-- Luo uuden urakan.
INSERT INTO urakka (nimi, alkupvm, loppupvm, hanke_sampoid, sampoid, tyyppi, hallintayksikko,
                    sopimustyyppi, urakkanro)
VALUES (:nimi, :alkupvm, :loppupvm, :hanke_sampoid, :sampoid, :urakkatyyppi :: urakkatyyppi, :hallintayksikko,
        :sopimustyyppi :: sopimustyyppi, :urakkanumero);

-- name: luo-harjassa-luotu-urakka<!
INSERT INTO urakka (nimi, alkupvm, loppupvm, alue, hallintayksikko, urakoitsija, hanke, tyyppi,
                    harjassa_luotu, luotu, luoja)
    VALUES (:nimi, :alkupvm, :loppupvm,
                   :alue, :hallintayksikko,
                   :urakoitsija, :hanke, 'vesivayla-hoito', TRUE,
                   NOW(), :kayttaja);

-- name: paivita-urakka!
-- Paivittaa urakan
UPDATE urakka
SET nimi          = :nimi,
  alkupvm         = :alkupvm,
  loppupvm        = :loppupvm,
  hanke_sampoid   = :hanke_sampoid,

  tyyppi          = :urakkatyyppi :: URAKKATYYPPI,
  hallintayksikko = :hallintayksikko,

  sopimustyyppi   = :sopimustyyppi :: SOPIMUSTYYPPI,
  urakkanro       = :urakkanro
WHERE id = :id;

-- name: paivita-harjassa-luotu-urakka!
-- Päivittää Harjassa luotua (vesiväylä)urakkaa
UPDATE urakka
  SET nimi = :nimi,
    alkupvm = :alkupvm,
    loppupvm = :loppupvm,
    alue = :alue,
    hallintayksikko = :hallintayksikko,
    urakoitsija = :urakoitsija,
    hanke = :hanke,
    muokattu = NOW(),
    muokkaaja = :kayttaja
WHERE id = :id AND harjassa_luotu IS TRUE;

-- name: paivita-tyyppi-hankkeen-urakoille!
-- Paivittaa annetun tyypin kaikille hankkeen urakoille
UPDATE urakka
SET tyyppi = :urakkatyyppi :: urakkatyyppi
WHERE hanke = (SELECT id
               FROM hanke
               WHERE sampoid = :hanke_sampoid);

-- name: hae-id-sampoidlla
-- Hakee urakan id:n sampo id:llä
SELECT urakka.id
FROM urakka
WHERE sampoid = :sampoid;

-- name: aseta-urakoitsija-sopimuksen-kautta!
-- Asettaa urakalle urakoitsijan sopimuksen Sampo id:n avulla
UPDATE urakka
SET urakoitsija = (
  SELECT id
  FROM organisaatio
  WHERE sampoid = (
    SELECT urakoitsija_sampoid
    FROM sopimus
    WHERE sampoid = :sopimus_sampoid))
WHERE sampoid = (
  SELECT urakka_sampoid
  FROM sopimus
  WHERE sampoid = :sopimus_sampoid AND
        paasopimus IS NULL);

-- name: aseta-urakoitsija-urakoille-yhteyshenkilon-kautta!
-- Asettaa urakoille urakoitsijan yhteyshenkilön Sampo id:n avulla
UPDATE urakka
SET urakoitsija = (
  SELECT id
  FROM organisaatio
  WHERE sampoid = :urakoitsija_sampoid)
WHERE sampoid IN (
  SELECT urakka_sampoid
  FROM sopimus
  WHERE urakoitsija_sampoid = :urakoitsija_sampoid AND
        paasopimus IS NULL);

-- name: hae-yksittainen-urakka
-- Hakee yhden urakan id:n avulla
SELECT
  u.id,
  u.nimi,
  u.sampoid,
  u.alue,
  u.alkupvm,
  u.loppupvm,
  u.tyyppi,
  u.sopimustyyppi,
  u.takuu_loppupvm,
  hal.id                                                        AS hallintayksikko_id,
  hal.nimi                                                      AS hallintayksikko_nimi,
  hal.lyhenne                                                   AS hallintayksikko_lyhenne,
  urk.id                                                        AS urakoitsija_id,
  urk.nimi                                                      AS urakoitsija_nimi,
  urk.ytunnus                                                   AS urakoitsija_ytunnus,
  yt.yhatunnus                                                  AS yha_yhatunnus,
  yt.yhaid                                                      AS yha_yhaid,
  yt.yhanimi                                                    AS yha_yhanimi,
  yt.elyt :: TEXT []                                            AS yha_elyt,
  yt.vuodet :: INTEGER []                                       AS yha_vuodet,
  yt.kohdeluettelo_paivitetty                                   AS yha_kohdeluettelo_paivitetty,
  yt.sidonta_lukittu                                            AS yha_sidonta_lukittu,
  (SELECT array_agg(concat(id, '=', sampoid))
   FROM sopimus s
   WHERE urakka = u.id)                                         AS sopimukset,
  ST_Simplify(au.alue, 50)                                      AS alueurakan_alue
FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
  LEFT JOIN alueurakka au ON u.urakkanro = au.alueurakkanro
  LEFT JOIN yhatiedot yt ON u.id = yt.urakka
WHERE u.id = :urakka_id;

-- name: hae-urakan-urakoitsija
-- Hakee valitun urakan urakoitsijan id:n
SELECT urakoitsija
FROM urakka
WHERE id = :urakka_id;

-- name: paivita-urakka-alueiden-nakyma
-- Päivittää urakka-alueiden materialisoidun näkymän
SELECT paivita_urakoiden_alueet();

-- name: hae-urakan-alueurakkanumero
-- Hakee urakan alueurakkanumeron
SELECT urakkanro AS alueurakkanro
FROM urakka
WHERE id = :id;

-- name: hae-aktiivisten-hoitourakoiden-alueurakkanumerot
-- Hakee käynnissäolevien hoitourakoiden alueurakkanumerot
SELECT
  u.id,
  u.hanke,
  u.nimi,
  u.urakkanro AS alueurakkanro
FROM urakka u
WHERE u.id IN (SELECT id
               FROM urakka
               WHERE (tyyppi = 'hoito' AND
                      u.hanke IS NOT NULL AND
                      (SELECT EXTRACT(YEAR FROM u.alkupvm)) <= :vuosi AND
                      :vuosi <= (SELECT EXTRACT(YEAR FROM u.loppupvm))));

-- name: hae-hallintayksikon-kaynnissa-olevat-urakat
-- Palauttaa nimen ja id:n hallintayksikön käynnissä olevista urakoista
SELECT
  id,
  nimi
FROM urakka
WHERE hallintayksikko = :hal
      AND (alkupvm IS NULL OR alkupvm <= current_date)
      AND (loppupvm IS NULL OR loppupvm >= current_date);

-- name: onko-urakalla-tehtavaa
SELECT EXISTS(
    SELECT tpk.id
    FROM toimenpidekoodi tpk
      INNER JOIN toimenpideinstanssi tpi
        ON tpi.toimenpide = tpk.emo
    WHERE
      tpi.urakka = :urakkaid AND
      tpk.id = :tehtavaid);

-- name: hae-urakka-sijainnilla
-- Hakee sijainnin ja urakan tyypin perusteella urakan. Urakan täytyy myös olla käynnissä.
SELECT u.id
FROM urakka u
  LEFT JOIN urakoiden_alueet ua ON u.id = ua.id
WHERE u.tyyppi = :urakkatyyppi :: urakkatyyppi
      AND (u.alkupvm IS NULL OR u.alkupvm <= current_timestamp)
      AND (u.loppupvm IS NULL OR u.loppupvm > current_timestamp)
      AND
      ((:urakkatyyppi = 'hoito' AND (st_contains(ua.alue, ST_MakePoint(:x, :y))))
      OR
      (:urakkatyyppi = 'valaistus' AND
       exists(SELECT id
              FROM valaistusurakka vu
              WHERE vu.valaistusurakkanro = u.urakkanro AND
                    st_dwithin(vu.alue, st_makepoint(:x, :y), :threshold)))
OR
((:urakkatyyppi = 'paallystys' OR :urakkatyyppi = 'paikkaus') AND
exists(SELECT id
FROM paallystyspalvelusopimus pps
WHERE pps.paallystyspalvelusopimusnro = u.urakkanro AND
st_dwithin(pps.alue, st_makepoint(:x, :y), :threshold)))
OR
((:urakkatyyppi = 'tekniset-laitteet') AND
exists(SELECT id
FROM tekniset_laitteet_urakka tlu
WHERE tlu.urakkanro = u.urakkanro AND
st_dwithin(tlu.alue, st_makepoint(:x, :y), :threshold)))
OR
((:urakkatyyppi = 'siltakorjaus') AND
exists(SELECT id
FROM siltapalvelusopimus sps
WHERE sps.urakkanro = u.urakkanro AND
st_dwithin(sps.alue, st_makepoint(:x, :y), :threshold))))
ORDER BY id ASC;

-- name: luo-alueurakka<!
INSERT INTO alueurakka (alueurakkanro, alue, elynumero)
VALUES (:alueurakkanro, ST_GeomFromText(:alue) :: GEOMETRY, :elynumero);

-- name: paivita-alueurakka!
UPDATE alueurakka
SET alue    = ST_GeomFromText(:alue) :: GEOMETRY,
elynumero = :elynumero
WHERE alueurakkanro = :alueurakkanro;

-- name: hae-alueurakka-numerolla
SELECT *
FROM alueurakka
WHERE alueurakkanro = :alueurakkanro;

-- name: tuhoa-alueurakkadata!
DELETE FROM alueurakka;

-- name: hae-urakan-geometria
SELECT
  u.alue          AS urakka_alue,
  alueurakka.alue AS alueurakka_alue
FROM urakka u
  LEFT JOIN alueurakka ON u.urakkanro = alueurakka.alueurakkanro
WHERE u.id = :id;

-- name: hae-urakoiden-geometriat
SELECT
  ST_Simplify(u.alue, :toleranssi)          AS urakka_alue,
  u.id                                      AS urakka_id,
  CASE
  WHEN (u.tyyppi = 'hoito'::urakkatyyppi AND alueurakka.alue IS NOT NULL)
    THEN
      hoidon_alueurakan_geometria(alueurakka.alueurakkanro)
  ELSE
    ST_Simplify(alueurakka.alue, :toleranssi)
  END AS alueurakka_alue
FROM urakka u
  LEFT JOIN alueurakka ON u.urakkanro = alueurakka.alueurakkanro
WHERE u.id IN (:idt);

-- name: hae-urakan-sampo-id
-- single?: true
SELECT sampoid
FROM urakka
WHERE id = :urakka;

-- name: hae-urakan-perustiedot-sampo-idlla
SELECT
  id,
  nimi,
  alkupvm,
  loppupvm,
  tyyppi
FROM urakka
WHERE sampoid = :sampoid;

-- name: aseta-takuun-loppupvm!
UPDATE urakka
SET takuu_loppupvm = :loppupvm
WHERE id = :urakka

-- name: aseta-urakan-indeksi!
UPDATE urakka
SET indeksi = :indeksi
WHERE id = :urakka

-- name: tuhoa-valaistusurakkadata!
DELETE FROM valaistusurakka;

-- name: hae-valaistusurakan-alueurakkanumero-sijainnilla
SELECT alueurakka
FROM valaistusurakka
WHERE st_dwithin(alue, st_makepoint(:x, :y), :treshold);

-- name: luo-valaistusurakka<!
INSERT INTO valaistusurakka (alueurakkanro, alue, valaistusurakkanro)
VALUES (:alueurakkanro, ST_GeomFromText(:alue) :: GEOMETRY, :valaistusurakka);

-- name: tuhoa-paallystyspalvelusopimusdata!
DELETE FROM paallystyspalvelusopimus;

-- name: hae-paallystyspalvelusopimus-alueurakkanumero-sijainnilla
SELECT alueurakka
FROM paallystyspalvelusopimus
WHERE st_dwithin(alue, st_makepoint(:x, :y), :treshold);

-- name: luo-paallystyspalvelusopimus<!
INSERT INTO paallystyspalvelusopimus (alueurakkanro, alue, paallystyspalvelusopimusnro)
VALUES (:alueurakkanro, ST_GeomFromText(:alue) :: GEOMETRY, :paallystyssopimus);

-- name: hae-lahin-hoidon-alueurakka
SELECT
  u.id,
  st_distance(au.alue, st_makepoint(:x, :y)) AS etaisyys
FROM urakka u
JOIN alueurakka au ON au.alueurakkanro = u.urakkanro
WHERE
u.alkupvm <= now() AND
u.loppupvm > now() AND
st_distance(au.alue, st_makepoint(:x, :y)) <= :maksimietaisyys
ORDER BY etaisyys ASC
LIMIT 1;

-- name: hae-kaynnissaoleva-urakka-urakkanumerolla
-- single? : true
SELECT
  u.id,
  u.sampoid,
  u.urakkanro,
  u.nimi,
  u.alkupvm,
  u.loppupvm,
  e.nimi        AS "elynimi",
  e.elynumero,
  o.nimi        AS "urakoitsija-nimi",
  o.ytunnus     AS "urakoitsija-ytunnus",
  o.katuosoite  AS "urakoitsija-katuosoite",
  o.postinumero AS "urakoitsija-postinumero"
FROM urakka u
  JOIN organisaatio e ON e.id = u.hallintayksikko
  JOIN organisaatio o ON o.id = u.urakoitsija
WHERE urakkanro = :urakkanro AND
      alkupvm <= now() AND
      loppupvm > now();

-- name: onko-olemassa-urakkanro?
-- single?: true
SELECT exists(SELECT id
              FROM urakka
              WHERE urakkanro = :urakkanro);

-- name: tuhoa-tekniset-laitteet-urakkadata!
DELETE FROM tekniset_laitteet_urakka;

-- name: hae-tekniset-laitteet-urakan-urakkanumero-sijainnilla
SELECT urakkanro
FROM tekniset_laitteet_urakka
WHERE st_dwithin(alue, st_makepoint(:x, :y), :treshold);

-- name: luo-tekniset-laitteet-urakka<!
INSERT INTO tekniset_laitteet_urakka (urakkanro, alue)
VALUES (:urakkanro, ST_GeomFromText(:alue) :: GEOMETRY);

-- name: tuhoa-siltapalvelusopimukset!
DELETE FROM siltapalvelusopimus;

-- name: hae-siltapalvelussopimuksen-urakkanumero-sijainnilla
SELECT urakkanro
FROM siltapalvelusopimus
WHERE st_dwithin(alue, st_makepoint(:x, :y), :treshold);

-- name: luo-siltapalvelusopimus<!
INSERT INTO siltapalvelusopimus (urakkanro, alue)
VALUES (:urakkanro, ST_GeomFromText(:alue) :: GEOMETRY);

-- name: hae-urakan-alkuvuosi
-- single?: true
SELECT EXTRACT(YEAR FROM alkupvm) :: INTEGER
FROM urakka
WHERE id = :urakka;

-- name: hae-urakan-ely
SELECT
  o.id,
  o.nimi,
  o.elynumero,
  o.lyhenne
FROM organisaatio o
  JOIN urakka u ON o.id = u.hallintayksikko
WHERE u.id = :urakkaid;

-- name: hae-urakat-joihin-jarjestelmalla-erillisoikeus
SELECT
  u.id,
  u.nimi,
  u.tyyppi,
  u.alkupvm,
  u.loppupvm,
  u.takuu_loppupvm,
  u.urakkanro AS alueurakkanumero,
  urk.nimi    AS urakoitsija_nimi,
  urk.ytunnus AS urakoitsija_ytunnus
FROM urakka u
  JOIN organisaatio urk ON u.urakoitsija = urk.id
  JOIN kayttajan_lisaoikeudet_urakkaan klu ON klu.urakka = u.id
  JOIN kayttaja k ON klu.kayttaja = k.id
WHERE k.kayttajanimi = :kayttajanimi
      AND k.jarjestelma;

-- name: hae-urakka-lahetettavaksi-sahkeeseen
-- Hakee urakan perustiedot id:llä APIa varten.
SELECT
  u.id,
  u.nimi,
  u.tyyppi,
  u.alkupvm,
  u.loppupvm,
  u.indeksi,
  u.takuu_loppupvm,
  u.urakkanro AS alueurakkanumero,
  u.hanke     AS "hanke-id",
  urk.nimi    AS urakoitsija_nimi,
  urk.ytunnus AS urakoitsija_ytunnus,
  y.id        AS "yhteyshenkilo-id",
  o.ytunnus   AS "urakoitsija-y-tunnus",
  o.nimi      AS "urakoitsijanimi"
FROM urakka u
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
  LEFT JOIN yhteyshenkilo_urakka yu ON u.id = yu.urakka AND yu.rooli = 'Sampo yhteyshenkilö'
  LEFT JOIN yhteyshenkilo y ON yu.yhteyshenkilo = y.id
  LEFT JOIN organisaatio o ON u.urakoitsija = o.id
WHERE u.id = :id;

-- name: kirjaa-sahke-lahetys!
INSERT INTO sahkelahetys (urakka, lahetetty, onnistunut)
VALUES (:urakka, now(), :onnistunut)
on CONFLICT  (urakka) do
update set lahetetty  = now(), onnistunut = :onnistunut;

-- name: perustettu-harjassa?
-- single?: true
SELECT exists(SELECT ''
              FROM urakka u
                JOIN sahkelahetys sl ON u.id = sl.urakka
              WHERE u.sampoid = :sampoid);

-- name: hae-urakat-joiden-lahetys-sahkeeseen-epaonnistunut
SELECT urakka
FROM sahkelahetys
WHERE onnistunut IS FALSE;

