-- name: tallenna-tyokonehavainto
-- Luo tai päivittää työkonehavainnon tietokantaan
SELECT tallenna_tai_paivita_tyokonehavainto(
       CAST(:jarjestelma AS character varying),
       CAST(:organisaationimi AS character varying),
       CAST(:ytunnus AS character varying),
       CAST(:viestitunniste AS integer),
       CAST(:lahetysaika AS timestamp),
       CAST(:tyokoneid AS integer),
       CAST(:tyokonetyyppi AS character varying),
       CAST(ST_MakePoint(:xkoordinaatti, :ykoordinaatti) as point),
       CAST(:urakkaid AS integer),
       CAST(:sopimusid AS integer),
       CAST(:tehtavat AS suoritettavatehtava[]))

-- name: tyokoneet-alueella
-- Etsii kaikki työkoneet annetulta alueelta
SELECT t.tyokoneid,
       t.jarjestelma,
       t.organisaatio,
       (SELECT nimi FROM organisaatio WHERE id=t.organisaatio) AS organisaationimi,
       t.viestitunniste,
       t.lahetysaika,
       t.vastaanotettu,
       t.tyokonetyyppi,
       t.sijainti,
       t.urakkaid,
       (SELECT nimi FROM urakka WHERE id=t.urakkaid) AS urakkanimi,
       t.sopimusid,
       t.tehtavat
FROM tyokonehavainto t
  WHERE ST_Contains(ST_MakeEnvelope(:xmin,:ymin,:xmax,:ymax), CAST(sijainti AS geometry))

-- name: urakan-tyokoneet-alueella
-- Etsii urakkaan liittyvät työkoneet annetulta alueelta
SELECT t.tyokoneid,
       t.jarjestelma,
       t.organisaatio,
       (SELECT nimi FROM organisaatio WHERE id=t.organisaatio) AS organisaationimi,
       t.viestitunniste,
       t.lahetysaika,
       t.vastaanotettu,
       t.tyokonetyyppi,
       t.sijainti,
       t.urakkaid,
       (SELECT nimi FROM urakka WHERE id=t.urakkaid) AS urakkanimi,
       t.sopimusid,
       t.tehtavat
FROM tyokonehavainto t
  WHERE urakkaid = :urakkaid
    AND ST_Contains(ST_MakeEnvelope(:xmin,:ymin,:xmax,:ymax), CAST(sijainti AS geometry))
