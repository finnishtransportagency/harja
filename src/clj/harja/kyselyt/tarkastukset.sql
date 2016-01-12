-- name: hae-urakan-tarkastukset
-- Hakee urakan tarkastukset aikavälin perusteella
SELECT
  t.id,
  sopimus,
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
  END AS tekija
FROM tarkastus t
  JOIN kayttaja k ON t.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
WHERE t.urakka = :urakka
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND (:rajaa_tyypilla = FALSE OR t.tyyppi = :tyyppi :: tarkastustyyppi);

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
  stm.hoitoluokka      AS soratiemittaus_hoitoluokka,
  stm.tasaisuus        AS soratiemittaus_tasaisuus,
  stm.kiinteys         AS soratiemittaus_kiinteys,
  stm.polyavyys        AS soratiemittaus_polyavyys,
  stm.sivukaltevuus    AS soratiemittaus_sivukaltevuus,
  thm.talvihoitoluokka AS talvihoitomittaus_hoitoluokka,
  thm.lumimaara        AS talvihoitomittaus_lumimaara,
  thm.tasaisuus        AS talvihoitomittaus_tasaisuus,
  thm.kitka            AS talvihoitomittaus_kitka,
  thm.lampotila        AS talvihoitomittaus_lampotila,
  thm.ajosuunta        AS talvihoitomittaus_ajosuunta
FROM tarkastus t
  LEFT JOIN kayttaja k ON t.luoja = k.id
  LEFT JOIN organisaatio o ON o.id = k.organisaatio
  LEFT JOIN soratiemittaus stm ON (t.tyyppi = 'soratie' :: tarkastustyyppi AND stm.tarkastus = t.id)
  LEFT JOIN talvihoitomittaus thm ON (t.tyyppi = 'talvihoito' :: tarkastustyyppi AND thm.tarkastus = t.id)
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
(talvihoitoluokka, lumimaara, tasaisuus, kitka, lampotila, ajosuunta, tarkastus)
VALUES (:talvihoitoluokka, :lumimaara, :tasaisuus, :kitka, :lampotila, :ajosuunta, :tarkastus)

-- name: paivita-talvihoitomittaus!
-- Päivittää tarkastuksen aiemmin luodun talvihoitomittauksen.
UPDATE talvihoitomittaus
SET talvihoitoluokka = :talvihoitoluokka,
  lumimaara          = :lumimaara,
  tasaisuus          = :tasaisuus,
  kitka              = :kitka,
  lampotila          = :lampotila,
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
  k.jarjestelma,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END AS tekija
FROM tarkastus t
  JOIN kayttaja k ON t.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka = :urakka
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'tiesto'::tarkastustyyppi
ORDER BY t.aika;

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
  k.jarjestelma,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END AS tekija
FROM tarkastus t
  JOIN kayttaja k ON t.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE t.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko)
      AND (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'tiesto'::tarkastustyyppi
ORDER BY t.aika;

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
  t.sijainti,
  t.tarkastaja,
  t.tyyppi,
  k.jarjestelma,
  liite.id   as liite_id,
  liite.nimi as liite_nimi,
  CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
    THEN 'urakoitsija' :: osapuoli
  ELSE 'tilaaja' :: osapuoli
  END AS tekija
FROM tarkastus t
  JOIN kayttaja k ON t.luoja = k.id
  JOIN organisaatio o ON k.organisaatio = o.id
  LEFT JOIN tarkastus_liite ON t.id = tarkastus_liite.tarkastus
  LEFT JOIN liite ON tarkastus_liite.liite = liite.id
WHERE (t.aika >= :alku AND t.aika <= :loppu)
      AND (:rajaa_tienumerolla = FALSE OR t.tr_numero = :tienumero)
      AND t.tyyppi = 'tiesto'::tarkastustyyppi
ORDER BY t.aika;