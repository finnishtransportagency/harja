-- name: hae-harjassa-luodut-urakat
-- Käytännössä kyse on vesiväyläurakoista
SELECT
  u.id,
  u.nimi,
  u.alkupvm,
  u.loppupvm,
  u.sampoid,
  u.urakkanro,
  hal.id        AS hallintayksikko_id,
  hal.nimi      AS hallintayksikko_nimi,
  urk.id        AS urakoitsija_id,
  urk.nimi      AS urakoitsija_nimi,
  urk.ytunnus   AS urakoitsija_ytunnus,
  s.nimi        AS sopimus_nimi,
  s.id          AS sopimus_id,
  s.paasopimus  AS "sopimus_paasopimus-id",
  h.nimi        AS hanke_nimi,
  h.id          AS hanke_id,
  sl.lahetetty  AS sahkelahetys_lahetetty,
  sl.id         AS sahkelahetys_id,
  sl.onnistunut AS sahkelahetys_onnistunut,
  array_to_string(ua.turvalaiteryhmat, ',') AS turvalaiteryhmat
FROM urakka u
  LEFT JOIN organisaatio hal ON u.hallintayksikko = hal.id
  LEFT JOIN organisaatio urk ON u.urakoitsija = urk.id
  LEFT JOIN hanke h ON u.hanke = h.id
  LEFT JOIN sopimus s ON u.id = s.urakka
  LEFT JOIN sahkelahetys sl ON sl.urakka = u.id
  LEFT JOIN vv_urakka_turvalaiteryhma ua ON u.id = ua.urakka
WHERE u.harjassa_luotu IS TRUE
ORDER BY u.alkupvm DESC, u.nimi;

-- name: luo-vesivaylaurakan-toimenpideinstanssi<!
INSERT INTO toimenpideinstanssi (urakka, nimi, toimenpide, alkupvm, loppupvm)
VALUES (:urakka_id, :nimi, (SELECT id
                            FROM toimenpide
                            WHERE nimi = :toimenpide_nimi), :alkupvm, :loppupvm);

-- name: luo-vesivaylaurakan-toimenpideinstanssin_vaylatyyppi<!
INSERT INTO toimenpideinstanssi_vesivaylat ("toimenpideinstanssi-id", vaylatyyppi)
VALUES (:toimenpideinstanssi_id, :vaylatyyppi :: vv_vaylatyyppi);

-- name: hae-lahimmat-urakat-aikavalilta
SELECT
  u.id,
  u.nimi,
  u.tyyppi,
  u.urakkanro,
  COALESCE(ST_Distance84(u.alue, st_makepoint(:x, :y)),
           ST_Distance84(au.alue, st_makepoint(:x, :y))) AS etaisyys
FROM urakka u
  LEFT JOIN alueurakka au ON au.alueurakkanro = u.urakkanro
WHERE
  -- Urakka on käynnissä
  (u.alkupvm <= current_date AND
   u.loppupvm >= current_date)
  OR
  -- Urakka on käynnissä (loppua ei tiedossa)
  (u.alkupvm <= current_date AND
   u.loppupvm IS NULL)
  OR
  -- Urakan takuuaika on voimassa
  (u.alkupvm <= current_date AND
   u.takuu_loppupvm >= current_date)
ORDER BY etaisyys;

-- name: hae-kaikki-urakat-aikavalilla
-- Palauttaa teiden-hoito-urakan hoitourakkana (MHU).
SELECT u.id        AS urakka_id,
       u.nimi      AS urakka_nimi,
       CASE
         WHEN u.tyyppi = 'teiden-hoito' THEN 'hoito'
         ELSE u.tyyppi
         END       AS tyyppi,
       o.id        AS hallintayksikko_id,
       o.nimi      AS hallintayksikko_nimi,
       o.elynumero AS hallintayksikko_elynumero,
       u.urakkanro AS urakka_urakkanro
FROM urakka u
         JOIN organisaatio o ON u.hallintayksikko = o.id
WHERE ((u.loppupvm >= :alku AND u.alkupvm <= :loppu) OR (u.loppupvm IS NULL AND u.alkupvm <= :loppu))
  AND (:urakoitsija :: INTEGER IS NULL OR :urakoitsija = u.urakoitsija)
  AND (:urakkatyyppi :: urakkatyyppi IS NULL OR CASE
                                                    WHEN :urakkatyyppi = 'hoito'
                                                        THEN u.tyyppi IN ('hoito', 'teiden-hoito')
                                                    ELSE u.tyyppi = :urakkatyyppi :: urakkatyyppi END)
  AND (:hallintayksikko_annettu = FALSE OR u.hallintayksikko IN (:hallintayksikko));

-- name: hae-kaynnissa-olevat-urakat
SELECT
  u.id,
  u.nimi,
  u.tyyppi
FROM urakka u
WHERE (u.alkupvm IS NULL OR u.alkupvm <= current_date)
      AND (u.loppupvm IS NULL OR u.loppupvm >= current_date);

-- name: hae-kaynnissa-olevat-urakkatyypin-urakat
SELECT
  u.id,
  u.nimi,
  u.tyyppi
FROM urakka u
WHERE (u.alkupvm IS NULL OR u.alkupvm <= current_date)
      AND (u.loppupvm IS NULL OR u.loppupvm >= current_date)
      AND (:urakkatyyppi IS NULL OR u.tyyppi = :urakkatyyppi :: urakkatyyppi);

-- name: hae-urakoiden-nimet
SELECT
    u.id,
    u.nimi,
    u.lyhyt_nimi
FROM urakka u
WHERE (:urakkatyyppi IS NULL OR u.tyyppi = :urakkatyyppi :: urakkatyyppi)
ORDER BY u.nimi ASC;

-- name: tallenna-urakan-lyhytnimi!
-- Vaihtaa urakan lyhytnimen
UPDATE urakka
SET lyhyt_nimi = :lyhytnimi
WHERE id = :urakka;

-- name: hae-kaynnissa-olevat-hoitourakat
SELECT
    u.id,
    u.nimi,
    u.tyyppi
  FROM urakka u
 WHERE (u.alkupvm IS NULL OR u.alkupvm <= current_date)
   AND (u.loppupvm IS NULL OR u.loppupvm >= current_date)
   AND u.tyyppi IN ('hoito', 'teiden-hoito');

-- name: hae-kaynnissa-olevat-ja-tulevat-urakat
SELECT
  u.id,
  u.nimi,
  u.tyyppi,
  u.alkupvm,
  u.loppupvm
FROM urakka u
WHERE u.alkupvm >= current_date
      OR (u.alkupvm <= current_date AND
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
  CASE WHEN u.tyyppi = 'paallystys' :: urakkatyyppi
      THEN ST_SimplifyPreserveTopology(u.alue, 50)
          END as alue,
  u.alkupvm,
  u.loppupvm,
  CASE
      WHEN u.kesakausi_alkupvm IS NOT NULL THEN
          CONCAT(TO_CHAR(NOW(), 'YYYY'), '-', TO_CHAR(u.kesakausi_alkupvm, 'MM-DD'))::DATE
      END AS "kesakausi-alkupvm",
  CASE
      WHEN u.kesakausi_loppupvm IS NOT NULL THEN
          CONCAT(TO_CHAR(NOW(), 'YYYY'), '-', TO_CHAR(u.kesakausi_loppupvm, 'MM-DD'))::DATE
      END AS "kesakausi-loppupvm",
  u.tyyppi,
  u.sopimustyyppi,
  u.indeksi,
  u.urakkanro,
  (SELECT *
   FROM indeksilaskennan_perusluku(u.id)) AS indeksilaskennan_perusluku,
  hal.id                                  AS hallintayksikko_id,
  hal.nimi                                AS hallintayksikko_nimi,
  hal.lyhenne                             AS hallintayksikko_lyhenne,
  urk.id                                  AS urakoitsija_id,
  urk.nimi                                AS urakoitsija_nimi,
  urk.ytunnus                             AS urakoitsija_ytunnus,
  yt.yhatunnus                            AS yha_yhatunnus,
  yt.yhaid                                AS yha_yhaid,
  yt.yhanimi                              AS yha_yhanimi,
  yt.elyt :: TEXT []                      AS yha_elyt,
  yt.vuodet :: INTEGER []                 AS yha_vuodet,
  yt.sidonta_lukittu                      AS yha_sidonta_lukittu,
  yt.kohdeluettelo_paivitetty             AS yha_kohdeluettelo_paivitetty,
  yt.kohdeluettelo_paivittaja             AS yha_kohdeluettelo_paivittaja,
  k.etunimi                               AS yha_kohdeluettelo_paivittaja_etunimi,
  k.sukunimi                              AS yha_kohdeluettelo_paivittaja_sukunimi,
  u.takuu_loppupvm,
  (SELECT array_agg(concat((CASE
                            WHEN paasopimus IS NULL
                              THEN '*'
                            ELSE '' END),
                           id,
                           '=',
                           COALESCE(sampoid, nimi)))
   FROM sopimus s
   WHERE urakka = u.id)                   AS sopimukset,
  -- Urakka-alue: tällä hetkellä tuetaan joko hoidon alueurakan, teknisten laitteiden ja siltapalvelusopimusten alueita.
  CASE
  WHEN u.tyyppi = 'siltakorjaus' :: urakkatyyppi
    THEN ST_Simplify(sps.alue, 50)
  WHEN u.tyyppi = 'tekniset-laitteet' :: urakkatyyppi
    THEN ST_Simplify(tlu.alue, 50)
  WHEN (u.tyyppi IN ('hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi) AND au.alue IS NOT NULL)
    THEN -- Luodaan yhtenäinen polygon alueurakan alueelle (multipolygonissa voi olla reikiä)
      ST_SimplifyPreserveTopology(hoidon_alueurakan_geometria(u.urakkanro), 50)
  END                                     AS alueurakan_alue

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

-- name: hae-urakkatiedot-laskutusyhteenvetoon
-- Listaa ELY-kohtaista laskutusyhteenvetoa varten aikavälillä käynnissäolevat hoitourakat
SELECT
  id,
  nimi,
  indeksi,
  tyyppi,
  alkupvm
FROM urakka u
WHERE :urakkaid :: INTEGER IS NULL AND
      u.hallintayksikko = :hallintayksikkoid AND
      u.tyyppi = :urakkatyyppi :: urakkatyyppi AND
      (u.alkupvm < :alkupvm AND u.loppupvm > :loppupvm OR
       u.alkupvm BETWEEN :alkupvm AND :loppupvm OR u.loppupvm BETWEEN :alkupvm AND :loppupvm) AND
      u.urakkanro IS NOT NULL
      OR u.id = :urakkaid :: INTEGER;

-- name: hae-urakan-organisaatio
-- Hakee urakan organisaation urakka-id:llä.
SELECT
  o.nimi,
  o.ytunnus
FROM organisaatio o
  JOIN urakka u ON o.id = u.urakoitsija
WHERE u.id = :urakka;

-- name: urakan-hallintayksikko
SELECT hallintayksikko AS "hallintayksikko-id"
FROM urakka
WHERE id = :id;

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
  hal.id                        AS hallintayksikko_id,
  hal.nimi                      AS hallintayksikko_nimi,
  hal.lyhenne                   AS hallintayksikko_lyhenne,
  urk.id                        AS urakoitsija_id,
  urk.nimi                      AS urakoitsija_nimi,
  urk.ytunnus                   AS urakoitsija_ytunnus,
  (SELECT array_agg(concat(id, '=', sampoid))
   FROM sopimus s
   WHERE urakka = u.id
         AND poistettu = FALSE) AS sopimukset,
  ST_Simplify(au.alue, 50)      AS alueurakan_alue
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
  hal.id                        AS hallintayksikko_id,
  hal.nimi                      AS hallintayksikko_nimi,
  hal.lyhenne                   AS hallintayksikko_lyhenne,
  urk.id                        AS urakoitsija_id,
  urk.nimi                      AS urakoitsija_nimi,
  urk.ytunnus                   AS urakoitsija_ytunnus,
  (SELECT array_agg(concat(id, '=', sampoid))
   FROM sopimus s
   WHERE urakka = u.id
         AND poistettu = FALSE) AS sopimukset,
  ST_Simplify(au.alue, 50)      AS alueurakan_alue
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
-- Palauttaa teiden-hoito-urakan hoitourakkana (MHU).
SELECT u.id         AS urakka_id,
       u.nimi       AS urakka_nimi,
       CASE
         WHEN u.tyyppi = 'teiden-hoito' THEN 'hoito'
         ELSE u.tyyppi
         END        AS tyyppi,
       hy.id        AS hallintayksikko_id,
       hy.nimi      AS hallintayksikko_nimi,
       hy.elynumero AS hallintayksikko_elynumero,
       u.urakkanro  AS urakka_urakkanro
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
WHERE s.urakka = :urakka
      AND s.poistettu = FALSE;

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
INSERT INTO urakka (nimi,
                    alkupvm,
                    loppupvm,
                    hanke_sampoid,
                    sampoid,
                    tyyppi,
                    hallintayksikko,
                    sopimustyyppi,
                    urakkanro,
                    urakoitsija)
VALUES (:nimi,
        :alkupvm,
        :loppupvm,
        :hanke_sampoid,
        :sampoid,
        :urakkatyyppi :: urakkatyyppi,
        :hallintayksikko,
        :sopimustyyppi :: sopimustyyppi,
        :urakkanumero,
        :urakoitsijaid);

-- name: luo-harjassa-luotu-urakka<!
INSERT INTO urakka (nimi,
                    urakkanro,
                    alkupvm,
                    loppupvm,
                    alue,
                    hallintayksikko,
                    urakoitsija,
                    hanke,
                    tyyppi,
                    harjassa_luotu,
                    luotu,
                    luoja,
                    sampoid)
VALUES (:nimi,
  :urakkanro,
  :alkupvm,
  :loppupvm,
  :alue,
  :hallintayksikko,
  :urakoitsija,
  :hanke,
  'vesivayla-hoito',
  TRUE,
  NOW(),
        :kayttaja,
        (SELECT 'PRHAR' || LPAD(currval(pg_get_serial_sequence('urakka', 'id')) :: TEXT, 5, '0')));

-- name: paivita-urakka!
-- Paivittaa urakan
UPDATE urakka
SET nimi          = :nimi,
  alkupvm         = :alkupvm,
  loppupvm        = :loppupvm,
  hanke_sampoid   = :hanke_sampoid,
  tyyppi          = :urakkatyyppi :: URAKKATYYPPI,
  hallintayksikko = :hallintayksikko,
  urakkanro       = :urakkanro,
  urakoitsija     = :urakoitsija

WHERE id = :id;

-- name: paivita-harjassa-luotu-urakka<!
-- Päivittää Harjassa luotua (vesiväylä)urakkaa
UPDATE urakka
SET nimi          = :nimi,
  urakkanro       = :urakkanro,
  alkupvm         = :alkupvm,
  loppupvm        = :loppupvm,
  alue            = :alue,
  hallintayksikko = :hallintayksikko,
  urakoitsija     = :urakoitsija,
  muokattu        = NOW(),
  muokkaaja       = :kayttaja
WHERE id = :id
      AND harjassa_luotu IS TRUE;

-- name: luo-tai-paivita-vesivaylaurakan-alue<!
INSERT INTO vv_urakka_turvalaiteryhma (
  urakka,
  turvalaiteryhmat,
  alkupvm,
  loppupvm,
  luotu,
  luoja)
VALUES (
  :urakka,
  :turvalaiteryhmat :: TEXT [],
  :alkupvm,
  :loppupvm,
  now(),
  :kayttaja)
ON CONFLICT (urakka)
  DO
  UPDATE SET
    turvalaiteryhmat = :turvalaiteryhmat :: TEXT [],
    alkupvm = :alkupvm::DATE,
    loppupvm = :loppupvm::DATE,
    muokattu         = now(),
    muokkaaja        = :kayttaja;

-- name: hae-vv-turvalaiteryhmien-nykyiset-urakat
SELECT id, nimi FROM urakka where
  array_append(ARRAY[]::int[], id)  &&
  (SELECT array_agg(urakka) from vv_urakka_turvalaiteryhma
            WHERE turvalaiteryhmat && string_to_array(replace (:turvalaiteryhmat, ' ', ''), ',') AND
                  urakka != :urakkaid) AND
                  (alkupvm, loppupvm) OVERLAPS (:alkupvm::DATE, :loppupvm::DATE);

-- name: hae-loytyvat-reimari-turvalaiteryhmat
SELECT tunnus FROM reimari_turvalaiteryhma
WHERE tunnus::text IN (:turvalaiteryhma);

-- name: tallenna-vv-urakkanro<!
-- Vesiväyläurakoissa urakkanro viittaa vv_urakka_turvalaiteryhma-tauluun,
-- jossa turvalaiteryhmät viittaavat reimari_turvalaiteryhma-tauluun, jonka
-- sisältö tulee Reimarista. Muissa urakkatyypeissä urakkanro viittaa muihin tauluihin, esim.
-- alueurakka-tauluun.
UPDATE urakka
SET urakkanro = :urakkanro,
    muokattu  = NOW(),
    muokkaaja = :kayttaja
WHERE id = :urakka;

--name: hae-velho-oid-lkm
-- Palauttaa velho_oid NOT NULL rivien lukumäärän
SELECT count(*) as lkm
FROM urakka
WHERE velho_oid IS NOT NULL;

--name: hae-kaikki-urakat-pvm
SELECT id, alkupvm, loppupvm
FROM urakka
WHERE velho_oid IS NOT NULL;

--name: hae-kaikki-urakka-velho-oid
SELECT velho_oid, id
FROM urakka
WHERE velho_oid IS NOT NULL;

-- name: paivita-tyyppi-hankkeen-urakoille!
-- Paivittaa annetun tyypin kaikille hankkeen urakoille
UPDATE urakka
SET tyyppi = :urakkatyyppi :: urakkatyyppi
WHERE hanke = (SELECT id
               FROM hanke
               WHERE sampoid = :hanke_sampoid);

-- name: paivita-velho_oid-null-kaikille!
-- Tyhjentää velho_oid tiedon kaikilta urakoilta
UPDATE urakka
SET velho_oid = NULL
WHERE velho_oid IS NOT NULL
  AND tyyppi IN ('hoito', 'teiden-hoito');

-- name: paivita-velho_oid-urakalle!
-- Päivittää velho_oid avaimen urakalle
UPDATE urakka
SET velho_oid = :velho_oid
WHERE urakkanro = :urakkanro
  AND tyyppi IN ('hoito', 'teiden-hoito');

-- name: hae-id-sampoidlla
-- Hakee urakan id:n sampo id:llä
SELECT urakka.id
FROM urakka
WHERE sampoid = :sampoid;

-- name: aseta-urakoitsija-sopimuksen-kautta!
-- Asettaa urakalle urakoitsijan sopimuksen Sampo id:n avulla
UPDATE urakka
SET urakoitsija = (SELECT id
                   FROM organisaatio
                   WHERE sampoid = (SELECT urakoitsija_sampoid
                                    FROM sopimus
                                    WHERE sampoid = :sopimus_sampoid))
WHERE sampoid = (SELECT urakka_sampoid
                 FROM sopimus
                 WHERE sampoid = :sopimus_sampoid
                       AND paasopimus IS NULL);

-- name: aseta-urakoitsija-urakoille-yhteyshenkilon-kautta!
-- Asettaa urakoille urakoitsijan yhteyshenkilön Sampo id:n avulla
UPDATE urakka
SET urakoitsija = (SELECT id
                   FROM organisaatio
                   WHERE sampoid = :urakoitsija_sampoid)
WHERE sampoid IN (SELECT urakka_sampoid
                  FROM sopimus
                  WHERE urakoitsija_sampoid = :urakoitsija_sampoid
                        AND paasopimus IS NULL);

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
  u.urakkanro,
  hal.id                        AS hallintayksikko_id,
  hal.nimi                      AS hallintayksikko_nimi,
  hal.lyhenne                   AS hallintayksikko_lyhenne,
  urk.id                        AS urakoitsija_id,
  urk.nimi                      AS urakoitsija_nimi,
  urk.ytunnus                   AS urakoitsija_ytunnus,
  yt.yhatunnus                  AS yha_yhatunnus,
  yt.yhaid                      AS yha_yhaid,
  yt.yhanimi                    AS yha_yhanimi,
  yt.elyt :: TEXT []            AS yha_elyt,
  yt.vuodet :: INTEGER []       AS yha_vuodet,
  yt.kohdeluettelo_paivitetty   AS yha_kohdeluettelo_paivitetty,
  yt.sidonta_lukittu            AS yha_sidonta_lukittu,
  (SELECT array_agg(concat(id, '=', sampoid))
   FROM sopimus s
   WHERE urakka = u.id
         AND poistettu = FALSE) AS sopimukset,
  ST_Simplify(au.alue, 50)      AS alueurakan_alue
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
  lpad(cast(u.urakkanro AS VARCHAR), 4, '0') AS alueurakkanro
FROM urakka u
WHERE u.id IN (SELECT id
               FROM urakka
               WHERE (tyyppi IN ('hoito', 'teiden-hoito') AND
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

-- name: hae-hallintayksikon-kaynnissa-olevat-urakkatyypin-urakat
-- Palauttaa nimen ja id:n hallintayksikön käynnissä olevista urakkatyypin urakoista
SELECT
  id,
  nimi
FROM urakka
WHERE hallintayksikko = :hal
      AND (alkupvm IS NULL OR alkupvm <= current_date)
      AND (loppupvm IS NULL OR loppupvm >= current_date)
      AND (:urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi :: urakkatyyppi);

-- name: onko-urakalla-tehtavaa
SELECT EXISTS(
    SELECT tpk.id
    FROM tehtava tpk
      INNER JOIN toimenpideinstanssi tpi ON tpi.toimenpide = tpk.emo
    WHERE tpi.urakka = :urakkaid
          AND tpk.id = :tehtavaid);


-- name: hae-urakka-sijainnilla
-- Hakee sijainnin ja urakan tyypin perusteella urakan. Urakan täytyy myös olla käynnissä.
-- Päättyvän urakan vastuu tieliikenneilmoituksista loppuu 1.10. klo 12. Siksi alkupvm ja loppupvm laskettu tunteja lisää.
SELECT u.id,
       u.nimi,
       u.tyyppi,
       u.alkupvm,
       u.loppupvm,
       u.takuu_loppupvm,
       u.urakkanro AS alueurakkanumero,
       urk.nimi    AS urakoitsija_nimi,
       urk.ytunnus AS urakoitsija_ytunnus,
       COALESCE(ST_Distance84(u.alue, st_makepoint(:x, :y)),
                ST_Distance84(vua.alue, st_makepoint(:x, :y)),
                ST_Distance84(pua.alue, st_makepoint(:x, :y))) AS etaisyys
FROM urakka u
         LEFT JOIN urakoiden_alueet ua ON u.id = ua.id
         LEFT JOIN valaistusurakka vua ON vua.valaistusurakkanro = u.urakkanro
         LEFT JOIN paallystyspalvelusopimus pua ON pua.paallystyspalvelusopimusnro = u.urakkanro
         JOIN organisaatio urk ON u.urakoitsija = urk.id
WHERE (CASE
           WHEN (:urakkatyyppi = 'hoito' OR :urakkatyyppi = 'teiden-hoito') THEN
               (u.tyyppi IN ('hoito', 'teiden-hoito') AND
                (u.alkupvm IS NULL OR u.alkupvm + interval '12 hour' <= current_timestamp)
                    AND (u.loppupvm IS NULL OR u.loppupvm + interval '36 hour' >= current_timestamp))
           ELSE (u.tyyppi = :urakkatyyppi :: urakkatyyppi
               AND (u.alkupvm IS NULL OR u.alkupvm <= current_date)
               AND (u.loppupvm IS NULL OR u.loppupvm >= current_date))
    END)
    AND ((:urakkatyyppi IN ('hoito', 'teiden-hoito') AND (st_contains(ua.alue, ST_MakePoint(:x, :y))))
    OR (:urakkatyyppi = 'valaistus' AND
        exists(SELECT id
               FROM valaistusurakka vu
               WHERE vu.valaistusurakkanro = u.urakkanro
                 AND st_dwithin(vu.alue, st_makepoint(:x, :y), :threshold)))
    OR ((:urakkatyyppi = 'paallystys' OR :urakkatyyppi = 'paikkaus') AND
        (CASE
             WHEN u.sopimustyyppi = 'palvelusopimus' THEN
                 EXISTS(SELECT id
                          FROM paallystyspalvelusopimus pps
                         WHERE pps.paallystyspalvelusopimusnro = u.urakkanro
                           AND st_dwithin(pps.alue, st_makepoint(:x, :y), :threshold))
            -- Kommentoitu toistaiseksi pois koska ilmoitusten urakan haku sijainnilla menee rikki.
            -- TODO Täytyykö pystyä hakemaan sijainnilla myös 'kokonaisurakka' sopimuksen piirissä olevia urakoita?
            --      Riittääkö keskittyminen pelkästään palvelusopimuksen piirissä oleviin päällystysurakoihin,
            --      vai tehdäänkö esim. erillinen 'sopimustyyppi' parametri, jolla tätä hakua voi suodattaa?
             --ELSE (u.sopimustyyppi = 'kokonaisurakka' AND
             --      (st_contains(ua.alue, st_makepoint(:x, :y))))
            END))
    OR ((:urakkatyyppi = 'tekniset-laitteet') AND
        exists(SELECT id
               FROM tekniset_laitteet_urakka tlu
               WHERE tlu.urakkanro = u.urakkanro
                 AND st_dwithin(tlu.alue, st_makepoint(:x, :y), :threshold)))
    OR ((:urakkatyyppi = 'siltakorjaus') AND
        exists(SELECT id
               FROM siltapalvelusopimus sps
               WHERE sps.urakkanro = u.urakkanro
                 AND st_dwithin(sps.alue, st_makepoint(:x, :y), :threshold))))
ORDER BY etaisyys ASC, u.alkupvm DESC;

-- name: hae-hoito-urakka-tr-pisteelle
SELECT id
FROM urakka
WHERE st_contains(alue,
    tierekisteriosoitteelle_piste(CAST(:tie AS INTEGER),CAST(:aosa AS INTEGER), CAST(:aet AS INTEGER)))
  AND tyyppi IN ('hoito', 'teiden-hoito')
  AND date(:paivamaara) BETWEEN alkupvm AND loppupvm
ORDER BY tyyppi DESC;

-- name: luo-alueurakka<!
INSERT INTO alueurakka (alueurakkanro, alue, elynumero, "ely-nimi", nimi, luotu, luoja)
VALUES (:alueurakkanro,
        ST_GeomFromText(:alue) :: GEOMETRY,
        :elynumero,
        :elynimi,
        :nimi,
        CURRENT_TIMESTAMP,
        (SELECT id
         FROM kayttaja
         WHERE kayttajanimi = 'Integraatio'));

-- name: paivita-alueurakka!
UPDATE alueurakka
SET alue     = ST_GeomFromText(:alue) :: GEOMETRY,
  elynumero  = :elynumero,
  "ely-nimi" = :elynimi,
  nimi       = :nimi,
  muokattu   = CURRENT_TIMESTAMP,
  muokkaaja  = (SELECT id
                FROM kayttaja
                WHERE kayttajanimi = 'Integraatio')
WHERE alueurakkanro = :alueurakkanro;

-- name: hae-alueurakka-numerolla
SELECT *
FROM alueurakka
WHERE alueurakkanro = :alueurakkanro;

-- name: tuhoa-alueurakkadata!
DELETE
FROM alueurakka;

-- name: hae-urakan-geometria
SELECT
  u.alue          AS urakka_alue,
  alueurakka.alue AS alueurakka_alue
FROM urakka u
  LEFT JOIN alueurakka ON u.urakkanro = alueurakka.alueurakkanro
WHERE u.id = :id;

-- name: hae-urakoiden-geometriat
SELECT
    u.id                             AS urakka_id,
    CASE
        WHEN (u.tyyppi IN ('hoito':: urakkatyyppi,'teiden-hoito':: urakkatyyppi)  AND alueurakka.alue IS NOT NULL)
            THEN ST_SimplifyPreserveTopology(hoidon_alueurakan_geometria(alueurakka.alueurakkanro), 50)
        ELSE ST_SimplifyPreserveTopology(hoidon_paaurakan_geometria(u.id), 50)
        END                              AS urakka_alue
  FROM urakka u
           LEFT JOIN alueurakka ON u.urakkanro = alueurakka.alueurakkanro
 WHERE u.id IN (:idt);

-- name: hae-urakan-sampo-id
-- single?: true
SELECT sampoid
FROM urakka
WHERE id = :urakka;

-- name: hae-urakan-nimi
SELECT id, nimi, sampoid
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
WHERE id = :urakka;

-- name: aseta-urakan-indeksi!
UPDATE urakka
SET indeksi = :indeksi
WHERE id = :urakka;

-- name: tuhoa-valaistusurakkadata!
DELETE
FROM valaistusurakka;

-- name: tarkista-valaistusurakkadata
SELECT count(*) as lkm
FROM valaistusurakka;

-- name: luo-valaistusurakka<!
INSERT INTO valaistusurakka (alue, valaistusurakkanro, paivitetty)
VALUES (ST_GeomFromText(:alue) :: GEOMETRY, :valaistusurakka, current_timestamp);

-- name: tuhoa-paallystyspalvelusopimusdata!
DELETE
FROM paallystyspalvelusopimus;

-- name: tarkista-paallystyspalvelusopimusdata
SELECT count(*) as lkm
FROM paallystyspalvelusopimus;

-- name: luo-paallystyspalvelusopimus<!
INSERT INTO paallystyspalvelusopimus (alue, paallystyspalvelusopimusnro, paivitetty)
VALUES (ST_GeomFromText(:alue) :: GEOMETRY, :paallystyssopimus, current_timestamp);

-- name: hae-lahin-hoidon-alueurakka
-- Päättyvän urakan vastuu tieliikenneilmoituksista loppuu 1.10. klo 12. Siksi alkupvm ja loppupvm laskettu tunteja lisää.
SELECT
  u.id,
  ST_Distance84(au.alue, st_makepoint(:x, :y)) AS etaisyys
FROM urakka u
         JOIN alueurakka au ON au.alueurakkanro = u.urakkanro
WHERE u.alkupvm + interval '12 hour' <= current_timestamp
  AND u.loppupvm + interval '36 hour' >= current_timestamp
  AND ST_Distance84(au.alue, st_makepoint(:x, :y)) <= :maksimietaisyys
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
WHERE urakkanro = :urakka
  AND alkupvm <= current_date
  AND loppupvm >= current_date
ORDER BY CASE WHEN u.tyyppi = 'hoito' THEN 1
              WHEN u.tyyppi = 'teiden-hoito' THEN 2
              WHEN u.tyyppi = 'paallystys' THEN 3
              WHEN u.tyyppi = 'tiemerkinta' THEN 4
              WHEN u.tyyppi = 'valaistus' THEN 5
              WHEN u.tyyppi = 'tekniset-laitteet' THEN 6
              WHEN u.tyyppi = 'siltakorjaus' THEN 7
              WHEN u.tyyppi = 'vesivayla-hoito' THEN 8
              WHEN u.tyyppi = 'vesivayla-kanavien-hoito' THEN 9
             END;

-- name: hae-kaynnissa-oleva-tieurakka-urakkanumerolla
-- single? : true
-- Tämä on vastaava, kuin yllä oleva haku, mutta tämä ei palauta mahdollisesti kanavaurakoita, koska urakkanumeorissa
-- on eri urakkatyyppien välillä ristiriitaisuutta.
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
WHERE u.urakkanro = :urakka
  AND u.alkupvm <= current_date
  AND u.loppupvm >= current_date
  AND u.tyyppi in ('hoito',
                 'teiden-hoito',
                 'paallystys',
                 'tiemerkinta',
                 'valaistus',
                 'tekniset-laitteet',
                 'siltakorjaus')
ORDER BY CASE WHEN u.tyyppi = 'hoito' THEN 1
              WHEN u.tyyppi = 'teiden-hoito' THEN 2
              WHEN u.tyyppi = 'paallystys' THEN 3
              WHEN u.tyyppi = 'tiemerkinta' THEN 4
              WHEN u.tyyppi = 'valaistus' THEN 5
              WHEN u.tyyppi = 'tekniset-laitteet' THEN 6
              WHEN u.tyyppi = 'siltakorjaus' THEN 7
             END;

-- name: onko-kaynnissa-urakkanro?
-- single?: true
SELECT exists(SELECT id
              FROM urakka
              WHERE urakkanro = :urakkanro
                AND alkupvm <= current_date
                AND loppupvm >= current_date);

-- name: tuhoa-tekniset-laitteet-urakkadata!
DELETE
FROM tekniset_laitteet_urakka;

-- name: hae-tekniset-laitteet-urakan-urakkanumero-sijainnilla
SELECT urakkanro
FROM tekniset_laitteet_urakka
WHERE st_dwithin(alue, st_makepoint(:x, :y), :treshold);

-- name: luo-tekniset-laitteet-urakka<!
INSERT INTO tekniset_laitteet_urakka (urakkanro, alue)
VALUES (:urakkanro, ST_GeomFromText(:alue) :: GEOMETRY);

-- name: tuhoa-siltapalvelusopimukset!
DELETE
FROM siltapalvelusopimus;

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

-- name: urakan-paivamaarat
SELECT
  alkupvm,
  loppupvm
FROM urakka
WHERE id = :id;

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

-- name: perustettu-harjassa?
-- single?: true
SELECT harjassa_luotu
FROM urakka u
WHERE u.sampoid = :sampoid;

-- name: hae-jarjestelmakayttajan-urakat
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
WHERE (exists(SELECT klu.id
              FROM kayttajan_lisaoikeudet_urakkaan klu
                JOIN kayttaja k ON klu.kayttaja = k.id
              WHERE klu.urakka = u.id
                    AND k.kayttajanimi = :kayttajanimi
                    AND k.jarjestelma)
       OR
       exists(SELECT o.id
              FROM organisaatio o
                JOIN kayttaja k ON o.id = k.organisaatio
              WHERE o.id = urk.id
                    AND k.kayttajanimi = :kayttajanimi
                    AND k.jarjestelma)
       OR
       exists(SELECT k.id
              FROM kayttaja k
                JOIN organisaatio o ON k.organisaatio = o.id
              WHERE k.kayttajanimi = :kayttajanimi
                    AND k.jarjestelma IS TRUE
                    AND o.tyyppi = 'liikennevirasto'))
      AND (:urakkatyyppi :: VARCHAR IS NULL OR u.tyyppi = :urakkatyyppi :: urakkatyyppi);

-- name: urakan-paasopimus-id
-- single?: true
SELECT id
FROM sopimus
WHERE urakka = :urakka
      AND paasopimus IS NULL
      AND poistettu = FALSE;

-- name: paivita-alue-urakalle!
UPDATE urakka
SET alue   = ST_GeomFromText(:alue) :: GEOMETRY,
  muokattu = CURRENT_TIMESTAMP
WHERE urakka.urakkanro = :urakkanro AND urakka.tyyppi IN ('hoito', 'teiden-hoito');

-- name: hae-urakka-id-alueurakkanumerolla
-- single?: true
SELECT id
FROM urakka
WHERE urakkanro = :alueurakka AND
      tyyppi IN ('hoito', 'teiden-hoito');

-- name: hae-urakat-tyypilla-ja-hallintayksikolla
SELECT u.id, u.nimi
FROM urakka u
WHERE u.tyyppi = :urakkatyyppi :: urakkatyyppi
  AND u.hallintayksikko = :hallintayksikko-id
  AND u.alkupvm < NOW() -- Urakan täytyy olla käynnissä
  AND u.loppupvm > NOW();

-- name: hae-urakan-hoitokaudet
SELECT alkupvm, loppupvm FROM urakan_hoitokaudet(:urakka_id);

-- name: listaa-urakat-analytiikalle-hoitovuosittain
-- Haetaan kaikki urakat ilman geometriatietoja
-- jos vuodet on annettu, niin rajaa haku voimassaolon perusteella
SELECT id, sampoid, nimi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, sopimustyyppi, indeksi,
       urakkanro as alueurakkanro, tyyppi, poistettu, velho_oid, luotu, muokattu
  FROM urakka
  WHERE (:alkuvuosi::INT IS NULL OR (alkupvm, loppupvm) OVERLAPS (concat(:alkuvuosi::text,'-10-01')::DATE, concat(:loppuvuosi::text,'-10-01')::DATE))
    AND tyyppi in ('hoito', 'teiden-hoito')
 ORDER BY alkupvm ASC;

-- name: listaa-kaikki-urakat-analytiikalle
-- Haetaan kaikki urakat ilman geometriatietoja
-- jos vuodet on annettu, niin rajaa haku voimassaolon perusteella
SELECT id, sampoid, nimi, alkupvm, loppupvm, hallintayksikko, urakoitsija, hanke, sopimustyyppi, indeksi,
       urakkanro as alueurakkanro, tyyppi, poistettu, velho_oid, luotu, muokattu
FROM urakka
ORDER BY alkupvm ASC;

-- name: hae-valaistusurakan-geometria
-- Simplifoidaan hieman geometriaa, koska niitä voi olla niin paljon.
SELECT ST_Simplify(v.alue, 20, true) as alue
  FROM valaistusurakka v
       JOIN urakka u ON u.id = :id AND u.urakkanro = v.valaistusurakkanro;
