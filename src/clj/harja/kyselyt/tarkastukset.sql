-- name: hae-urakan-tarkastukset
-- Hakee urakan tarkastukset aikavälin perusteella
SELECT
  t.id,
  t.sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.sijainti,
  t.tarkastaja,
  t.tyyppi,
  t.nayta_urakoitsijalle   AS "nayta-urakoitsijalle",
  (SELECT normalisoi_talvihoitoluokka(thm.talvihoitoluokka::INTEGER, t.aika)) AS talvihoitomittaus_hoitoluokka,
  thm.lumimaara            AS talvihoitomittaus_lumimaara,
  thm.tasaisuus            AS talvihoitomittaus_tasaisuus,
  thm.kitka                AS talvihoitomittaus_kitka,
  thm.lampotila_tie        AS talvihoitomittaus_lampotila_tie,
  thm.lampotila_ilma       AS talvihoitomittaus_lampotila_ilma,
  stm.hoitoluokka          AS soratiemittaus_hoitoluokka,
  stm.tasaisuus            AS soratiemittaus_tasaisuus,
  stm.kiinteys             AS soratiemittaus_kiinteys,
  stm.polyavyys            AS soratiemittaus_polyavyys,
  stm.sivukaltevuus        AS soratiemittaus_sivukaltevuus,
  ypk.tr_numero            AS yllapitokohde_tr_numero,
  ypk.tr_alkuosa           AS yllapitokohde_tr_alkuosa,
  ypk.tr_alkuetaisyys      AS yllapitokohde_tr_alkuetaisyys,
  ypk.tr_loppuosa          AS yllapitokohde_tr_loppuosa,
  ypk.tr_loppuetaisyys     AS yllapitokohde_tr_loppuetaisyys,
  ypk.kohdenumero          AS yllapitokohde_numero,
  ypk.nimi                 AS yllapitokohde_nimi,
  liite.id                 AS liite_id,
  liite.nimi               AS liite_nimi,
  liite.tyyppi             AS liite_tyyppi,
  liite.koko               AS liite_koko,
  liite.liite_oid          AS liite_oid,
  k.jarjestelma,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END                      AS tekija,
  (SELECT array_agg(nimi)
   FROM tarkastus_vakiohavainto t_vh
     JOIN vakiohavainto vh ON t_vh.vakiohavainto = vh.id
   WHERE tarkastus = t.id) AS vakiohavainnot
FROM tarkastus t
  LEFT JOIN talvihoitomittaus thm ON thm.tarkastus = t.id
  LEFT JOIN soratiemittaus stm ON stm.tarkastus = t.id
  LEFT JOIN kayttaja k ON t.luoja = k.id
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
  LEFT JOIN yllapitokohde ypk ON t.yllapitokohde = ypk.id
  LEFT JOIN tarkastus_liite tl ON tl.tarkastus = t.id
  LEFT JOIN liite ON tl.liite = liite.id
WHERE t.urakka = :urakka
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.aika BETWEEN :alku AND :loppu
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND (:rajaa_tyypilla = FALSE OR t.tyyppi = :tyyppi :: tarkastustyyppi)
      AND (:havaintoja_sisaltavat = FALSE
           OR ((char_length(t.havainnot) > 0 AND lower(t.havainnot) != 'ok')
               OR EXISTS(SELECT tarkastus
                         FROM tarkastus_vakiohavainto
                         WHERE tarkastus = t.id)
               OR EXISTS(SELECT tarkastus
                         FROM talvihoitomittaus
                         WHERE tarkastus = t.id)
               OR EXISTS(SELECT tarkastus
                         FROM soratiemittaus
                         WHERE tarkastus = t.id)))
      AND (:vain_laadunalitukset = FALSE OR t.laadunalitus = TRUE)
      AND (:tekija::varchar IS NULL OR
           :tekija = 'tilaaja' AND o.tyyppi != 'urakoitsija'::organisaatiotyyppi OR
           :tekija = 'urakoitsija' AND o.tyyppi = 'urakoitsija'::organisaatiotyyppi)
      AND t.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE)
ORDER BY t.aika DESC
LIMIT :maxrivimaara;

-- name: hae-urakan-tarkastukset-kartalle
-- fetch-size: 64
-- row-fn: geo/muunna-reitti
SELECT
  ST_Simplify(t.sijainti, :toleranssi) AS reitti,
  t.tyyppi,
  t.laadunalitus,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END                                  AS tekija,
  -- Talvihoito- ja soratiemittauksesta riittää tieto, onko niitä tarkastuksella
  CASE WHEN
      thm.lumimaara IS NULL AND
      thm.tasaisuus IS NULL AND
      thm.kitka IS NULL AND
      thm.lampotila_ilma IS NULL AND
      thm.lampotila_tie IS NULL
    THEN NULL
    ELSE 'Talvihoitomittaus'
  END AS talvihoitomittaus,
  CASE WHEN
      stm.tasaisuus IS NULL AND
      stm.kiinteys IS NULL AND
      stm.polyavyys IS NULL AND
      stm.sivukaltevuus IS NULL
    THEN NULL
    ELSE 'Soratiemittaus'
  END AS soratiemittaus,
  -- Vakiohavainnot otetaan merkkijonona, koska tyyliin vaikuttaa tietyt avainsanat (esim. "Luminen")
  (SELECT array_agg(nimi)
   FROM tarkastus_vakiohavainto t_vh
     JOIN vakiohavainto vh ON t_vh.vakiohavainto = vh.id
   WHERE tarkastus = t.id)             AS vakiohavainnot,
  t.havainnot AS havainnot
FROM tarkastus t
  LEFT JOIN kayttaja k ON t.luoja = k.id
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
  -- Talvi- ja soratiemittaukset
  LEFT JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  LEFT JOIN soratiemittaus stm ON t.id = stm.tarkastus
WHERE t.urakka = :urakka
      AND t.sijainti IS NOT NULL
      AND (:valittu::INTEGER IS NULL OR t.id = :valittu)
      AND ST_Intersects(t.envelope, ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND (:rajaa_tyypilla = FALSE OR t.tyyppi = :tyyppi :: tarkastustyyppi)
      AND (:havaintoja_sisaltavat = FALSE
           OR ((char_length(t.havainnot) > 0 AND lower(t.havainnot) != 'ok')
               OR EXISTS(SELECT tarkastus
                         FROM tarkastus_vakiohavainto
                         WHERE tarkastus = t.id)
               OR EXISTS(SELECT tarkastus
                         FROM talvihoitomittaus
                         WHERE tarkastus = t.id)
               OR EXISTS(SELECT tarkastus
                         FROM soratiemittaus
                         WHERE tarkastus = t.id)))
      AND (:vain_laadunalitukset = FALSE OR t.laadunalitus = TRUE)
      AND (:tekija::varchar IS NULL OR
           :tekija = 'tilaaja' AND o.tyyppi != 'urakoitsija'::organisaatiotyyppi OR
           :tekija = 'urakoitsija' AND o.tyyppi = 'urakoitsija'::organisaatiotyyppi)
      AND t.poistettu IS NOT TRUE;

-- name: hae-urakan-tarkastusten-asiat-kartalle
-- Hakee pisteessä löytyneet tarkastukset karttaa klikattaessa
SELECT
  t.id,
  t.tyyppi,
  t.laadunalitus,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END                                                        AS tekija,
  t.aika,
  t.tarkastaja,
  t.havainnot,
  (SELECT array_agg(nimi)
   FROM tarkastus_vakiohavainto t_vh
     JOIN vakiohavainto vh ON t_vh.vakiohavainto = vh.id
   WHERE tarkastus = t.id)                                   AS vakiohavainnot,
  (SELECT normalisoi_talvihoitoluokka(thm.talvihoitoluokka::INTEGER, t.aika)) AS talvihoitomittaus_hoitoluokka,
  thm.lumimaara            AS talvihoitomittaus_lumimaara,
  thm.tasaisuus            AS talvihoitomittaus_tasaisuus,
  thm.kitka                AS talvihoitomittaus_kitka,
  thm.lampotila_tie        AS talvihoitomittaus_lampotila_tie,
  thm.lampotila_ilma       AS talvihoitomittaus_lampotila_ilma,
  stm.hoitoluokka          AS soratiemittaus_hoitoluokka,
  stm.tasaisuus            AS soratiemittaus_tasaisuus,
  stm.kiinteys             AS soratiemittaus_kiinteys,
  stm.polyavyys            AS soratiemittaus_polyavyys,
  stm.sivukaltevuus        AS soratiemittaus_sivukaltevuus,
  yrita_tierekisteriosoite_pisteille2(
      alkupiste(t.sijainti), loppupiste(t.sijainti), 1)::TEXT AS tierekisteriosoite
FROM tarkastus t
  LEFT JOIN kayttaja k ON t.luoja = k.id
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
  LEFT JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  LEFT JOIN soratiemittaus stm ON t.id = stm.tarkastus
WHERE t.urakka = :urakka
      AND t.sijainti IS NOT NULL
      AND ST_Distance84(t.sijainti, ST_MakePoint(:x, :y)) < :toleranssi
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND (:rajaa_tyypilla = FALSE OR t.tyyppi = :tyyppi :: tarkastustyyppi)
      AND (:havaintoja_sisaltavat = FALSE
           OR ((char_length(t.havainnot) > 0 AND lower(t.havainnot) != 'ok')
               OR EXISTS(SELECT tarkastus
                         FROM tarkastus_vakiohavainto
                         WHERE tarkastus = t.id)
               OR EXISTS(SELECT tarkastus
                         FROM talvihoitomittaus
                         WHERE tarkastus = t.id)
               OR EXISTS(SELECT tarkastus
                         FROM soratiemittaus
                         WHERE tarkastus = t.id)))
      AND (:vain_laadunalitukset = FALSE OR t.laadunalitus = TRUE)
      AND t.poistettu IS NOT TRUE;

-- name: hae-tarkastus
-- Hakee yhden urakan tarkastuksen tiedot id:llä.
SELECT
  t.id,
  t.sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.sijainti,
  t.tarkastaja,
  t.tyyppi,
  t.havainnot,
  t.laadunalitus,
  t.luoja,
  t.yllapitokohde,
  o.nimi                   AS organisaatio,
  k.kayttajanimi,
  k.jarjestelma,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END                      AS tekija,
  (SELECT array_agg(nimi)
   FROM tarkastus_vakiohavainto t_vh
     JOIN vakiohavainto vh ON t_vh.vakiohavainto = vh.id
   WHERE tarkastus = t.id) AS vakiohavainnot,
  stm.hoitoluokka          AS soratiemittaus_hoitoluokka,
  stm.tasaisuus            AS soratiemittaus_tasaisuus,
  stm.kiinteys             AS soratiemittaus_kiinteys,
  stm.polyavyys            AS soratiemittaus_polyavyys,
  stm.sivukaltevuus        AS soratiemittaus_sivukaltevuus,
  stm.tarkastus            AS soratiemittaus_tarkastus,
  (SELECT normalisoi_talvihoitoluokka(thm.talvihoitoluokka::INTEGER, t.aika)) AS talvihoitomittaus_hoitoluokka,
  thm.lumimaara            AS talvihoitomittaus_lumimaara,
  thm.tasaisuus            AS talvihoitomittaus_tasaisuus,
  thm.kitka                AS talvihoitomittaus_kitka,
  thm.lampotila_tie        AS talvihoitomittaus_lampotila_tie,
  thm.lampotila_ilma       AS talvihoitomittaus_lampotila_ilma,
  thm.ajosuunta            AS talvihoitomittaus_ajosuunta,
  thm.tarkastus            AS talvihoitomittaus_tarkastus,
  tl.laatupoikkeama        AS laatupoikkeamaid,
  t.nayta_urakoitsijalle   AS "nayta-urakoitsijalle"
FROM tarkastus t
  LEFT JOIN kayttaja k ON t.luoja = k.id
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
  LEFT JOIN soratiemittaus stm ON ((t.tyyppi = 'soratie' :: tarkastustyyppi
                                    OR
                                    t.tyyppi = 'laatu' :: tarkastustyyppi)
                                   AND stm.tarkastus = t.id)
  LEFT JOIN talvihoitomittaus thm ON ((t.tyyppi = 'talvihoito' :: tarkastustyyppi OR
                                       t.tyyppi = 'laatu' :: tarkastustyyppi)
                                      AND thm.tarkastus = t.id)
  LEFT JOIN tarkastus_laatupoikkeama tl ON t.id = tl.tarkastus
WHERE t.urakka = :urakka
      AND t.id = :id
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);

-- name: hae-tarkastuksen-liitteet
-- Hakee annetun tarkastuksen kaikki liitteet
SELECT
  l.id,
  l.tyyppi,
  l.koko,
  l.nimi,
  l.liite_oid AS oid
FROM liite l
  JOIN tarkastus_liite tl ON l.id = tl.liite
WHERE tl.tarkastus = :tarkastus
ORDER BY l.luotu ASC;

-- name: luo-tarkastus<!
-- Luo uuden tarkastuksen
INSERT
INTO tarkastus
(lahde, urakka, aika, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
 sijainti, tarkastaja, tyyppi, luoja, ulkoinen_id, havainnot, laadunalitus, yllapitokohde, nayta_urakoitsijalle)
VALUES (:lahde :: lahde, :urakka, :aika, :tr_numero, :tr_alkuosa, :tr_alkuetaisyys, :tr_loppuosa, :tr_loppuetaisyys,
                         :sijainti, :tarkastaja, :tyyppi :: tarkastustyyppi, :luoja, :ulkoinen_id,
        :havainnot, :laadunalitus, :yllapitokohde, :nayta_urakoitsijalle);

-- name: luodun-tarkastuksen-id
-- single?: true
-- Koska tarkastuksen luonti ohjataan triggerillä eri tauluun, ei luo-tarkastus<! palauta oikein
-- id kenttää. Tällä haetaan viimeksi luodun arvo.
SELECT currval('tarkastus_id_seq');

-- name: paivita-tarkastus!
-- Päivittää tarkastuksen tiedot
UPDATE tarkastus
SET aika               = :aika,
  tr_numero            = :tr_numero,
  tr_alkuosa           = :tr_alkuosa,
  tr_alkuetaisyys      = :tr_alkuetaisyys,
  tr_loppuosa          = :tr_loppuosa,
  tr_loppuetaisyys     = :tr_loppuetaisyys,
  sijainti             = :sijainti,
  tarkastaja           = :tarkastaja,
  tyyppi               = :tyyppi :: tarkastustyyppi,
  muokkaaja            = :muokkaaja,
  muokattu             = current_timestamp,
  havainnot            = :havainnot,
  laadunalitus         = :laadunalitus,
  yllapitokohde        = :yllapitokohde,
  nayta_urakoitsijalle = :nayta_urakoitsijalle,
  poistettu            = FALSE
WHERE urakka = :urakka AND id = :id;

-- name: poista-tarkastus!
UPDATE tarkastus
SET muokattu = NOW(), muokkaaja = :kayttajanimi, poistettu = TRUE
WHERE urakka = :urakka-id AND ulkoinen_id IN (:ulkoiset-idt) AND poistettu IS NOT TRUE;

-- name: luo-talvihoitomittaus<!
-- Luo uuden talvihoitomittauksen annetulle tarkastukselle.
INSERT
INTO talvihoitomittaus
(talvihoitoluokka, lumimaara, tasaisuus, kitka, lampotila_ilma, lampotila_tie, ajosuunta, tarkastus)
VALUES (:talvihoitoluokka, :lumimaara, :tasaisuus, :kitka, :lampotila_ilma, :lampotila_tie, :ajosuunta, :tarkastus)

-- name: paivita-talvihoitomittaus!
-- Päivittää tarkastuksen aiemmin luodun talvihoitomittauksen.
UPDATE talvihoitomittaus
SET talvihoitoluokka = :talvihoitoluokka,
  lumimaara          = :lumimaara,
  tasaisuus          = :tasaisuus,
  kitka              = :kitka,
  lampotila_ilma     = :lampotila_ilma,
  lampotila_tie      = :lampotila_tie,
  ajosuunta          = :ajosuunta
WHERE tarkastus = :tarkastus;

--name: poista-talvihoitomittaus!
DELETE
FROM talvihoitomittaus
WHERE tarkastus = :tarkastus;

-- name: luo-soratiemittaus<!
-- Luo uuden soratiemittauksen annetulle tarkastukselle.
INSERT
INTO soratiemittaus
(hoitoluokka, tasaisuus, kiinteys, polyavyys, sivukaltevuus, tarkastus)
VALUES (:hoitoluokka, :tasaisuus, :kiinteys, :polyavyys, :sivukaltevuus, :tarkastus);

-- name: paivita-soratiemittaus!
-- Päivittää tarkastuksen aiemmin luodun soratiemittauksen
UPDATE soratiemittaus
SET hoitoluokka = :hoitoluokka,
  tasaisuus     = :tasaisuus,
  kiinteys      = :kiinteys,
  polyavyys     = :polyavyys,
  sivukaltevuus = :sivukaltevuus
WHERE tarkastus = :tarkastus;

--name: poista-soratiemittaus!
DELETE
FROM soratiemittaus
WHERE tarkastus = :tarkastus;

-- name: hae-tarkastus-ulkoisella-idlla-ja-tyypilla
-- Hakee tarkastuksen ja sen havainnon id:t ulkoisella id:lla ja luojalla.
SELECT id
FROM tarkastus
WHERE ulkoinen_id = :id
      AND tyyppi = :tyyppi :: tarkastustyyppi
      AND luoja = :luoja;

-- name: luo-liite<!
-- Luo tarkastukselle liite
INSERT INTO tarkastus_liite (tarkastus, liite) VALUES (:tarkastus, :liite)

-- name: hae-urakan-tiestotarkastukset-liitteineen-raportille
-- Hakee urakan tiestötarkastukset aikavälin perusteella raportille
SELECT
  t.id,
  sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.sijainti,
  t.tarkastaja,
  t.tyyppi,
  liite.id        AS liite_id,
  liite.nimi      AS liite_nimi,
  liite.tyyppi    AS liite_tyyppi,
  liite.koko      AS liite_koko,
  liite.liite_oid AS liite_oid
FROM tarkastus t
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka = :urakka
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'tiesto' :: tarkastustyyppi
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);

-- name: hae-hallintayksikon-tiestotarkastukset-liitteineen-raportille
-- Hakee urakan tiestötarkastukset aikavälin perusteella raportille
SELECT
  t.id,
  sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.sijainti,
  t.tarkastaja,
  t.tyyppi,
  u.nimi          AS urakka,
  liite.id        AS liite_id,
  liite.nimi      AS liite_nimi,
  liite.tyyppi    AS liite_tyyppi,
  liite.koko      AS liite_koko,
  liite.liite_oid AS liite_oid
FROM tarkastus t
  JOIN urakka u ON (t.urakka = u.id AND u.urakkanro IS NOT NULL)
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka IN (SELECT id
                   FROM urakka
                   WHERE hallintayksikko = :hallintayksikko
                         AND (:urakkatyyppi IS NULL OR (
                            CASE WHEN :urakkatyyppi = 'hoito' THEN tyyppi IN ('hoito', 'teiden-hoito')
                            ELSE tyyppi = :urakkatyyppi::urakkatyyppi
                            END)))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'tiesto' :: tarkastustyyppi
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);

-- name: hae-koko-maan-tiestotarkastukset-liitteineen-raportille
-- Hakee urakan tiestötarkastukset aikavälin perusteella raportille
SELECT
  t.id,
  sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.tarkastaja,
  t.tyyppi,
  u.nimi          AS urakka,
  liite.id        AS liite_id,
  liite.nimi      AS liite_nimi,
  liite.tyyppi    AS liite_tyyppi,
  liite.koko      AS liite_koko,
  liite.liite_oid AS liite_oid
FROM tarkastus t
  JOIN urakka u ON (t.urakka = u.id AND u.urakkanro IS NOT NULL)
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka IN (SELECT id
                   FROM urakka
                   WHERE (:urakkatyyppi :: urakkatyyppi IS NULL OR (
                       CASE WHEN :urakkatyyppi = 'hoito' THEN tyyppi IN ('hoito', 'teiden-hoito')
                       ELSE tyyppi = :urakkatyyppi::urakkatyyppi
                       END)))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'tiesto' :: tarkastustyyppi
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);

-- name: hae-urakan-kelitarkastukset-liitteineen-raportille
-- Hakee urakan kelitarkastukset (talvihoitomittaukset) aikavälin perusteella raportille
SELECT
  t.id,
  sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.tarkastaja,
  t.tyyppi,
  (SELECT normalisoi_talvihoitoluokka(thm.talvihoitoluokka::INTEGER, t.aika)) as talvihoitoluokka,
  thm.lumimaara,
  thm.tasaisuus,
  thm.kitka,
  thm.lampotila_ilma,
  thm.lampotila_tie,
  thm.ajosuunta,
  liite.id        AS liite_id,
  liite.nimi      AS liite_nimi,
  liite.tyyppi    AS liite_tyyppi,
  liite.koko      AS liite_koko,
  liite.liite_oid AS liite_oid
FROM tarkastus t
  LEFT JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka = :urakka
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'talvihoito' :: tarkastustyyppi
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);

-- name: hae-hallintayksikon-kelitarkastukset-liitteineen-raportille
-- Hakee hallintayksikön kelitarkastukset (talvihoitomittaukset) aikavälin perusteella raportille
SELECT
  t.id,
  sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.tarkastaja,
  t.tyyppi,
  (SELECT normalisoi_talvihoitoluokka(thm.talvihoitoluokka::INTEGER, t.aika)) as talvihoitoluokka,
  thm.lumimaara,
  thm.tasaisuus,
  thm.kitka,
  thm.lampotila_ilma,
  thm.lampotila_tie,
  thm.ajosuunta,
  u.nimi          AS urakka,
  liite.id        AS liite_id,
  liite.nimi      AS liite_nimi,
  liite.tyyppi    AS liite_tyyppi,
  liite.koko      AS liite_koko,
  liite.liite_oid AS liite_oid
FROM tarkastus t
  JOIN urakka u ON (t.urakka = u.id AND u.urakkanro IS NOT NULL)
  LEFT JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka IN (SELECT id
                   FROM urakka
                   WHERE hallintayksikko = :hallintayksikko
                         AND (:urakkatyyppi :: urakkatyyppi IS NULL OR
                              CASE WHEN :urakkatyyppi = 'hoito' THEN -- huomioidaan myös teiden-hoito -urakkatyyppi
                                    tyyppi IN  ('hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi)
                                    ELSE tyyppi = :urakkatyyppi::urakkatyyppi
                         END))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'talvihoito' :: tarkastustyyppi
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);

-- name: hae-koko-maan-kelitarkastukset-liitteineen-raportille
-- Hakee koko maan kelitarkastukset (talvihoitomittaukset) aikavälin perusteella raportille
SELECT
  t.id,
  sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.sijainti,
  t.tarkastaja,
  t.tyyppi,
  (SELECT normalisoi_talvihoitoluokka(thm.talvihoitoluokka::INTEGER, t.aika)) as talvihoitoluokka,
  thm.lumimaara,
  thm.tasaisuus,
  thm.kitka,
  thm.lampotila_ilma,
  thm.lampotila_tie,
  thm.ajosuunta,
  u.nimi          AS urakka,
  liite.id        AS liite_id,
  liite.nimi      AS liite_nimi,
  liite.tyyppi    AS liite_tyyppi,
  liite.koko      AS liite_koko,
  liite.liite_oid AS liite_oid
FROM tarkastus t
  JOIN urakka u ON (t.urakka = u.id AND u.urakkanro IS NOT NULL)
  LEFT JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka IN (SELECT id
                   FROM urakka
                   WHERE (:urakkatyyppi :: urakkatyyppi IS NULL OR
                          CASE WHEN :urakkatyyppi = 'hoito'  -- huomioidaan myös teiden-hoito -urakkatyyppi
                               THEN tyyppi IN ('hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi)
                               ELSE tyyppi = :urakkatyyppi::urakkatyyppi
                       END))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'talvihoito' :: tarkastustyyppi
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);

-- name: hae-urakan-soratietarkastukset-raportille
-- Hakee urakan soratietarkastukset aikavälin perusteella raportille
SELECT
  t.id,
  sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.tarkastaja,
  t.tyyppi,
  st_length(t.sijainti) AS tr_metrit,
  stm.hoitoluokka,
  stm.tasaisuus,
  stm.kiinteys,
  stm.polyavyys,
  stm.sivukaltevuus
FROM tarkastus t
  LEFT JOIN soratiemittaus stm ON t.id = stm.tarkastus
WHERE t.urakka = :urakka
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'soratie' :: tarkastustyyppi
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);

-- name: hae-hallintayksikon-soratietarkastukset-raportille
-- Hakee hallintayksikön soratietarkastukset aikavälin perusteella raportille
SELECT
  t.id,
  sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.tarkastaja,
  t.tyyppi,
  st_length(t.sijainti) AS tr_metrit,
  stm.hoitoluokka,
  stm.tasaisuus,
  stm.kiinteys,
  stm.polyavyys,
  stm.sivukaltevuus,
  u.nimi                AS urakka
FROM tarkastus t
  LEFT JOIN soratiemittaus stm ON t.id = stm.tarkastus
  JOIN urakka u ON (t.urakka = u.id AND u.urakkanro IS NOT NULL)
WHERE t.urakka IN (SELECT id
                   FROM urakka u
                   WHERE hallintayksikko = :hallintayksikko
                         AND (:urakkatyyppi :: urakkatyyppi IS NULL OR (
                       CASE WHEN :urakkatyyppi = 'hoito' THEN u.tyyppi IN ('hoito', 'teiden-hoito')
                            ELSE u.tyyppi = :urakkatyyppi :: urakkatyyppi
                       END)))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'soratie' :: tarkastustyyppi
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);

-- name: hae-koko-maan-soratietarkastukset-raportille
-- Hakee koko maan soratietarkastukset aikavälin perusteella raportille
SELECT
  t.id,
  sopimus,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.tarkastaja,
  t.tyyppi,
  st_length(t.sijainti) AS tr_metrit,
  stm.hoitoluokka,
  stm.tasaisuus,
  stm.kiinteys,
  stm.polyavyys,
  stm.sivukaltevuus,
  u.nimi                AS urakka
FROM tarkastus t
  LEFT JOIN soratiemittaus stm ON t.id = stm.tarkastus
  JOIN urakka u ON (t.urakka = u.id AND u.urakkanro IS NOT NULL)
WHERE t.urakka IN (SELECT id
                   FROM urakka u
                   WHERE (:urakkatyyppi :: urakkatyyppi IS NULL OR (
                       CASE WHEN :urakkatyyppi = 'hoito' THEN u.tyyppi IN ('hoito','teiden-hoito')
                            ELSE u.tyyppi = :urakkatyyppi :: urakkatyyppi
                       END)))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'soratie' :: tarkastustyyppi
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.poistettu IS NOT TRUE
      -- Ei kuulu poistettuun ylläpitokohteeseen
      AND (t.yllapitokohde IS NULL
          OR
          t.yllapitokohde IS NOT NULL AND
            (SELECT poistettu FROM yllapitokohde WHERE id = t.yllapitokohde) IS NOT TRUE);

-- name: liita-tarkastukselle-laatupoikkeama<!
INSERT INTO tarkastus_laatupoikkeama (tarkastus, laatupoikkeama) VALUES (:tarkastus, :laatupoikkeama);

-- name: liita-tarkastuksen-liitteet-laatupoikkeamalle<!
INSERT INTO laatupoikkeama_liite (laatupoikkeama, liite)
  SELECT
    :laatupoikkeama,
    liite
  FROM tarkastus_liite
  WHERE tarkastus = :tarkastus;

-- name: hae-laaduntarkastukset
-- Hakee laaduntarkastukset joko urakalle, hallintayksikölle tai koko maalle.
SELECT
  t.id,
  t.aika,
  t.tr_numero,
  t.tr_alkuosa,
  t.tr_alkuetaisyys,
  t.tr_loppuosa,
  t.tr_loppuetaisyys,
  t.havainnot,
  t.laadunalitus,
  t.tarkastaja,
  t.tyyppi,
  st_length(t.sijainti)                                           AS tr_metrit,
  u.nimi                                                          AS urakka,
  liite.id                                                        AS liite_id,
  liite.nimi                                                      AS liite_nimi,
  liite.tyyppi                                                    AS liite_tyyppi,
  liite.koko                                                      AS liite_koko,
  liite.liite_oid                                                 AS liite_oid,
  stm.tarkastus                                                   AS soratiemittaus_id,
  stm.hoitoluokka                                                 AS soratiemittaus_hoitoluokka,
  stm.tasaisuus                                                   AS soratiemittaus_tasaisuus,
  stm.kiinteys                                                    AS soratiemittaus_kiinteys,
  stm.polyavyys                                                   AS soratiemittaus_polyavyys,
  stm.sivukaltevuus                                               AS soratiemittaus_sivukaltevuus,
  thm.tarkastus                                                   AS talvihoitomittaus_id,
  (SELECT normalisoi_talvihoitoluokka(thm.talvihoitoluokka::INTEGER, t.aika)) AS talvihoitomittaus_hoitoluokka,
  thm.lumimaara                                                   AS talvihoitomittaus_lumimaara,
  thm.tasaisuus                                                   AS talvihoitomittaus_tasaisuus,
  thm.kitka                                                       AS talvihoitomittaus_kitka,
  thm.ajosuunta                                                   AS talvihoitomittaus_ajosuunta,
  thm.lampotila_tie                                               AS talvihoitomittaus_lampotila_tie,
  thm.lampotila_ilma                                              AS talvihoitomittaus_lampotila_ilma,
  array(SELECT vh.nimi
        FROM vakiohavainto vh, tarkastus_vakiohavainto tvh
        WHERE vh.id = tvh.vakiohavainto AND tvh.tarkastus = t.id) AS vakiohavainnot
FROM tarkastus t
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
  JOIN urakka u ON t.urakka = u.id
  LEFT JOIN soratiemittaus stm ON stm.tarkastus = t.id
  LEFT JOIN talvihoitomittaus thm ON thm.tarkastus = t.id
WHERE t.tyyppi = 'laatu' :: tarkastustyyppi
      AND (t.aika BETWEEN :alku AND :loppu)
      AND (:tienumero :: INTEGER IS NULL OR t.tr_numero = :tienumero)
      AND ((:urakka :: INTEGER IS NULL AND u.urakkanro IS NOT NULL) OR t.urakka = :urakka)
      AND (:hallintayksikko :: INTEGER IS NULL OR u.hallintayksikko = :hallintayksikko)
      AND (:laadunalitus :: BOOLEAN IS NULL OR t.laadunalitus = :laadunalitus)
      AND (t.nayta_urakoitsijalle IS TRUE OR :kayttaja_on_urakoitsija IS FALSE)
      AND t.poistettu IS NOT TRUE;

--name: hae-tarkastusajon-reittipisteet
SELECT id, sijainti, havainnot
  FROM tarkastusreitti
 WHERE tarkastusajo = :tarkastusajoid;

--name: nayta-tarkastus-urakoitsijalle<!
UPDATE tarkastus SET nayta_urakoitsijalle = TRUE
WHERE id = :id;

--name: tarkastuksen-urakka
SELECT urakka FROM tarkastus WHERE id = :id;
