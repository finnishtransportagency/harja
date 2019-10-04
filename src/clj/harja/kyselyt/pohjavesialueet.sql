-- name: hae-pohjavesialueet
-- Hakee pohjavesialueet annetulle hallintayksikölle
SELECT
  id,
  nimi,
  alue,
  tunnus
FROM pohjavesialueet_hallintayksikoittain
WHERE hallintayksikko = :hallintayksikko AND suolarajoitus IS TRUE;

-- name: hae-urakan-pohjavesialueet
-- Hakee hoidon alueurakan alueella olevat pohjavesialueet
SELECT
  p_u.nimi,
  p_u.tunnus,
  p_u.alue,
  p.tr_numero AS numero,
  p.tr_alkuosa AS aosa,
  p.tr_alkuetaisyys AS aet,
  p.tr_loppuosa AS losa,
  p.tr_loppuetaisyys AS let
FROM pohjavesialueet_urakoittain p_u
JOIN pohjavesialue p ON p.tunnus = p_u.tunnus
WHERE p_u.urakka = :urakka-id AND p_u.suolarajoitus IS TRUE
ORDER BY p_u.tunnus, numero, aosa, aet, losa, let;

-- name: hae-urakan-pohjavesialueet-teittain
-- Hakee hoidon alueurakan alueella olevat pohjavesialueet teittain
SELECT DISTINCT
  p.nimi,
  p.tunnus,
  p.tr_numero AS tie,
  pa.alue
FROM (SELECT DISTINCT nimi, tunnus, tr_numero FROM pohjavesialue) AS p
  LEFT JOIN pohjavesialueet_urakoittain pa ON pa.tunnus = p.tunnus
WHERE pa.urakka = :urakka-id AND pa.suolarajoitus IS TRUE ORDER BY p.nimi ASC;

-- name: poista-pohjavesialueet!
-- Poistaa kaikki pohjavesialueet
DELETE FROM pohjavesialue;

-- name: luo-pohjavesialue!
INSERT INTO pohjavesialue
    (nimi,
     tunnus,
     alue,
     suolarajoitus,
     tr_numero,
     tr_alkuosa,
     tr_alkuetaisyys,
     tr_loppuosa,
     tr_loppuetaisyys,
     tr_ajorata,
     luotu,
     luoja,
     aineisto_id) VALUES
    (:nimi,
     :tunnus,
     ST_GeomFromText(:geometria) :: GEOMETRY,
     :suolarajoitus,
     :tr_numero,
     :tr_alkuosa,
     :tr_alkuetaisyys,
     :tr_loppuosa,
     :tr_loppuetaisyys,
     :tr_ajorata,
     current_timestamp,
     (select id from kayttaja where kayttajanimi = 'Integraatio'),
     :aineisto_id);

-- name: paivita-pohjavesialueet
SELECT paivita_pohjavesialueet();

-- name: paivita-pohjavesialue-kooste
SELECT paivita_pohjavesialue_kooste();
