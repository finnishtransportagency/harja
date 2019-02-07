-- name: tallenna-tyokonehavainto<!
-- Luo tai päivittää työkonehavainnon tietokantaan
INSERT INTO tyokonehavainto
       (jarjestelma, organisaatio, viestitunniste, lahetysaika,
        tyokoneid, tyokonetyyppi, sijainti, urakkaid, tehtavat, suunta)
VALUES (:jarjestelma,
        (SELECT id FROM organisaatio WHERE ytunnus=:ytunnus),
        :viestitunniste, CAST(:lahetysaika AS TIMESTAMP), :tyokoneid, :tyokonetyyppi,
	ST_MakePoint(:xkoordinaatti, :ykoordinaatti)::GEOMETRY,
	:urakkaid, :tehtavat::suoritettavatehtava[], :suunta);

-- name: tallenna-tyokonehavainto-viivageometrialla<!
-- Luo tai päivittää työkonehavainnon tietokantaan, kun sijainti on saatu viivageometriana (LineString)
INSERT INTO tyokonehavainto
               (jarjestelma,
               organisaatio,
               viestitunniste,
               lahetysaika,
               tyokoneid,
               tyokonetyyppi,
               sijainti,
               urakkaid,
               tehtavat,
               suunta)
VALUES (:jarjestelma,
        (SELECT id FROM organisaatio WHERE ytunnus=:ytunnus),
                :viestitunniste,
                CAST(:lahetysaika AS TIMESTAMP),
                :tyokoneid,
                :tyokonetyyppi,
                ST_GeomFromGeoJSON(:viivageometria),
                :urakkaid,
                :tehtavat::suoritettavatehtava[],
                 :suunta);

-- name: poista-vanhentuneet-havainnot!
-- Poistaa vanhentuneet havainnot työkoneseurannasta, jos edellinen havainto > 5h vanha
DELETE FROM tyokonehavainto
 WHERE vastaanotettu < NOW() - INTERVAL '5 hours';
