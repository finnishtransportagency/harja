-- name: tallenna-tyokonehavainto
-- Luo tai päivittää työkonehavainnon tietokantaan
SELECT tallenna_tai_paivita_tyokonehavainto(
    CAST(:jarjestelma AS CHARACTER VARYING),
    CAST(:organisaationimi AS CHARACTER VARYING),
    CAST(:ytunnus AS CHARACTER VARYING),
    CAST(:viestitunniste AS INTEGER),
    CAST(:lahetysaika AS TIMESTAMP),
    CAST(:tyokoneid AS INTEGER),
    CAST(:tyokonetyyppi AS CHARACTER VARYING),
    CAST(ST_MakePoint(:xkoordinaatti, :ykoordinaatti) AS POINT),
    CAST(:suunta AS REAL),
    CAST(:urakkaid AS INTEGER),
    CAST(:tehtavat AS suoritettavatehtava []));

-- name: tyokoneet-alueella
-- Etsii kaikki työkoneet annetulta alueelta
SELECT
  t.tyokoneid,
  t.jarjestelma,
  t.organisaatio,
  (SELECT nimi
   FROM organisaatio
   WHERE id = t.organisaatio) AS organisaationimi,
  t.viestitunniste,
  t.lahetysaika,
  t.vastaanotettu,
  t.tyokonetyyppi,
  t.sijainti,
  t.suunta,
  t.edellinensijainti,
  t.urakkaid,
  (SELECT nimi
   FROM urakka
   WHERE id = t.urakkaid)     AS urakkanimi,
  t.tehtavat
FROM tyokonehavainto t
WHERE ST_Contains(ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax), CAST(sijainti AS GEOMETRY));

-- name: urakan-tyokoneet-alueella
-- Etsii urakkaan liittyvät työkoneet annetulta alueelta
SELECT
  t.tyokoneid,
  t.jarjestelma,
  t.organisaatio,
  (SELECT nimi
   FROM organisaatio
   WHERE id = t.organisaatio) AS organisaationimi,
  t.viestitunniste,
  t.lahetysaika,
  t.vastaanotettu,
  t.tyokonetyyppi,
  t.sijainti,
  t.suunta,
  t.edellinensijainti,
  t.urakkaid,
  (SELECT nimi
   FROM urakka
   WHERE id = t.urakkaid)     AS urakkanimi,
  t.tehtavat
FROM tyokonehavainto t
WHERE urakkaid = :urakkaid
      AND ST_Contains(ST_MakeEnvelope(:xmin, :ymin, :xmax, :ymax), CAST(sijainti AS GEOMETRY));

-- name: poista-vanhentuneet-havainnot!
-- Poistaa vanhentuneet havainnot työkoneseurannasta
DELETE FROM tyokonehavainto
WHERE vastaanotettu < NOW() - INTERVAL '2 hours'
