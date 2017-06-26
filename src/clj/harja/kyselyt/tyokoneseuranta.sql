-- name: tallenna-tyokonehavainto<!
-- Luo tai päivittää työkonehavainnon tietokantaan
INSERT INTO tyokonehavainto
       (jarjestelma, organisaatio, viestitunniste, lahetysaika,
        tyokoneid, tyokonetyyppi, sijainti, urakkaid, tehtavat, suunta)
VALUES (:jarjestelma,
        (SELECT id FROM organisaatio WHERE ytunnus=:ytunnus),
        :viestitunniste, CAST(:lahetysaika AS TIMESTAMP), :tyokoneid, :tyokonetyyppi,
	ST_MakePoint(:xkoordinaatti, :ykoordinaatti)::POINT,
	:urakkaid, :tehtavat::suoritettavatehtava[], :suunta);


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
-- Poistaa vanhentuneet havainnot työkoneseurannasta, jos edellinen havainto > 5h vanha
DELETE FROM tyokonehavainto
 WHERE vastaanotettu < NOW() - INTERVAL '5 hours';
