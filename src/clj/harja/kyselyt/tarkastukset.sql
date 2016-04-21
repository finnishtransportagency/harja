-- name: hae-urakan-tarkastukset
-- Hakee urakan tarkastukset aikavälin perusteella
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
  t.sijainti,
  t.tarkastaja,
  t.tyyppi,
  k.jarjestelma,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END AS tekija,
  (SELECT array_agg(nimi) FROM tarkastus_vakiohavainto t_vh
    JOIN vakiohavainto vh ON t_vh.vakiohavainto = vh.id
  WHERE tarkastus = t.id) as vakiohavainnot
FROM tarkastus t
  LEFT JOIN kayttaja k ON t.luoja = k.id
  LEFT JOIN organisaatio o ON k.organisaatio = o.id
WHERE t.urakka = :urakka
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND (:rajaa_tyypilla = FALSE OR t.tyyppi = :tyyppi :: tarkastustyyppi)
LIMIT :maxrivimaara;

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
  t.luoja,
  o.nimi        AS organisaatio,
  k.kayttajanimi,
  k.jarjestelma,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END AS tekija,
  (SELECT array_agg(nimi) FROM tarkastus_vakiohavainto t_vh
    JOIN vakiohavainto vh ON t_vh.vakiohavainto = vh.id
  WHERE tarkastus = t.id) as vakiohavainnot,
  stm.hoitoluokka      AS soratiemittaus_hoitoluokka,
  stm.tasaisuus        AS soratiemittaus_tasaisuus,
  stm.kiinteys         AS soratiemittaus_kiinteys,
  stm.polyavyys        AS soratiemittaus_polyavyys,
  stm.sivukaltevuus    AS soratiemittaus_sivukaltevuus,
  thm.talvihoitoluokka AS talvihoitomittaus_hoitoluokka,
  thm.lumimaara        AS talvihoitomittaus_lumimaara,
  thm.tasaisuus        AS talvihoitomittaus_tasaisuus,
  thm.kitka            AS talvihoitomittaus_kitka,
  thm.lampotila_tie    AS talvihoitomittaus_lampotila_tie,
  thm.lampotila_ilma   AS talvihoitomittaus_lampotila_ilma,
  thm.ajosuunta        AS talvihoitomittaus_ajosuunta,
  tl.laatupoikkeama    AS laatupoikkeamaid
FROM tarkastus t
  LEFT JOIN kayttaja k ON t.luoja = k.id
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
  LEFT JOIN soratiemittaus stm ON (t.tyyppi = 'soratie' :: tarkastustyyppi AND stm.tarkastus = t.id)
  LEFT JOIN talvihoitomittaus thm ON (t.tyyppi = 'talvihoito' :: tarkastustyyppi AND thm.tarkastus = t.id)
  LEFT JOIN tarkastus_laatupoikkeama tl ON t.id = tl.tarkastus
WHERE t.urakka = :urakka AND t.id = :id;

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
(urakka, aika, tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
 sijainti, tarkastaja, tyyppi, luoja, ulkoinen_id, havainnot)
VALUES (:urakka, :aika, :tr_numero, :tr_alkuosa, :tr_alkuetaisyys, :tr_loppuosa, :tr_loppuetaisyys,
                 :sijainti, :tarkastaja, :tyyppi :: tarkastustyyppi, :luoja, :ulkoinen_id,
        :havainnot);

-- name: paivita-tarkastus!
-- Päivittää tarkastuksen tiedot
UPDATE tarkastus
SET aika           = :aika,
  tr_numero        = :tr_numero,
  tr_alkuosa       = :tr_alkuosa,
  tr_alkuetaisyys  = :tr_alkuetaisyys,
  tr_loppuosa      = :tr_loppuosa,
  tr_loppuetaisyys = :tr_loppuetaisyys,
  sijainti         = :sijainti,
  tarkastaja       = :tarkastaja,
  tyyppi           = :tyyppi :: tarkastustyyppi,
  muokkaaja        = :muokkaaja,
  muokattu         = current_timestamp,
  havainnot        = :havainnot
WHERE urakka = :urakka AND id = :id;

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
WHERE tarkastus = :tarkastus

-- name: luo-soratiemittaus<!
-- Luo uuden soratiemittauksen annetulle tarkastukselle.
INSERT
INTO soratiemittaus
(hoitoluokka, tasaisuus, kiinteys, polyavyys, sivukaltevuus, tarkastus)
VALUES (:hoitoluokka, :tasaisuus, :kiinteys, :polyavyys, :sivukaltevuus, :tarkastus)

-- name: paivita-soratiemittaus!
-- Päivittää tarkastuksen aiemmin luodun soratiemittauksen
UPDATE soratiemittaus
SET hoitoluokka = :hoitoluokka,
  tasaisuus     = :tasaisuus,
  kiinteys      = :kiinteys,
  polyavyys     = :polyavyys,
  sivukaltevuus = :sivukaltevuus
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
  t.sijainti,
  t.tarkastaja,
  t.tyyppi,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid
FROM tarkastus t
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka = :urakka
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'tiesto'::tarkastustyyppi;

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
  t.sijainti,
  t.tarkastaja,
  t.tyyppi,
  u.nimi as urakka,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid
FROM tarkastus t
  JOIN urakka u ON t.urakka = u.id
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko
                   AND (:urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi :: urakkatyyppi))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'tiesto'::tarkastustyyppi;

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
  t.tarkastaja,
  t.tyyppi,
  u.nimi as urakka,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid
FROM tarkastus t
  JOIN urakka u ON t.urakka = u.id
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka IN (SELECT id FROM urakka WHERE (:urakkatyyppi::urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi :: urakkatyyppi))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'tiesto'::tarkastustyyppi;

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
  t.tarkastaja,
  t.tyyppi,
  thm.talvihoitoluokka,
  thm.lumimaara,
  thm.tasaisuus,
  thm.kitka,
  thm.lampotila_ilma,
  thm.lampotila_tie,
  thm.ajosuunta,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid
FROM tarkastus t
  JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka = :urakka
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'talvihoito'::tarkastustyyppi;

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
  t.tarkastaja,
  t.tyyppi,
  thm.talvihoitoluokka,
  thm.lumimaara,
  thm.tasaisuus,
  thm.kitka,
  thm.lampotila_ilma,
  thm.lampotila_tie,
  thm.ajosuunta,
  u.nimi as urakka,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid
FROM tarkastus t
  JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  JOIN urakka u ON t.urakka = u.id
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko
                   AND (:urakkatyyppi::urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi :: urakkatyyppi))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'talvihoito'::tarkastustyyppi;

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
  t.sijainti,
  t.tarkastaja,
  t.tyyppi,
  thm.talvihoitoluokka,
  thm.lumimaara,
  thm.tasaisuus,
  thm.kitka,
  thm.lampotila_ilma,
  thm.lampotila_tie,
  thm.ajosuunta,
  u.nimi as urakka,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  liite.tyyppi as liite_tyyppi,
  liite.koko as liite_koko,
  liite.liite_oid as liite_oid
FROM tarkastus t
  JOIN talvihoitomittaus thm ON t.id = thm.tarkastus
  JOIN urakka u ON t.urakka = u.id
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka IN (SELECT id FROM urakka WHERE (:urakkatyyppi::urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi :: urakkatyyppi))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'talvihoito'::tarkastustyyppi;

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
  t.tarkastaja,
  t.tyyppi,
  st_length(t.sijainti) as tr_metrit,
  stm.hoitoluokka,
  stm.tasaisuus,
  stm.kiinteys,
  stm.polyavyys,
  stm.sivukaltevuus
FROM tarkastus t
  JOIN soratiemittaus stm ON t.id = stm.tarkastus
WHERE t.urakka = :urakka
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'soratie'::tarkastustyyppi;

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
  t.tarkastaja,
  t.tyyppi,
  st_length(t.sijainti) as tr_metrit,
  stm.hoitoluokka,
  stm.tasaisuus,
  stm.kiinteys,
  stm.polyavyys,
  stm.sivukaltevuus,
  u.nimi as urakka
FROM tarkastus t
  JOIN soratiemittaus stm ON t.id = stm.tarkastus
  JOIN urakka u ON t.urakka = u.id
WHERE t.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko
                   AND (:urakkatyyppi::urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi :: urakkatyyppi))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'soratie'::tarkastustyyppi;

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
  t.tarkastaja,
  t.tyyppi,
  st_length(t.sijainti) as tr_metrit,
  stm.hoitoluokka,
  stm.tasaisuus,
  stm.kiinteys,
  stm.polyavyys,
  stm.sivukaltevuus,
  u.nimi as urakka
FROM tarkastus t
  JOIN soratiemittaus stm ON t.id = stm.tarkastus
  JOIN urakka u ON t.urakka = u.id
WHERE t.urakka IN (SELECT id FROM urakka WHERE (:urakkatyyppi::urakkatyyppi IS NULL OR tyyppi = :urakkatyyppi :: urakkatyyppi))
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'soratie'::tarkastustyyppi;

-- name: liita-tarkastukselle-laatupoikkeama<!
INSERT INTO tarkastus_laatupoikkeama (tarkastus, laatupoikkeama) VALUES (:tarkastus, :laatupoikkeama);

-- name: liita-tarkastuksen-liitteet-laatupoikkeamalle<!
INSERT INTO laatupoikkeama_liite (laatupoikkeama, liite)
  SELECT
    :laatupoikkeama,
    liite
  FROM tarkastus_liite
  WHERE tarkastus = :tarkastus;