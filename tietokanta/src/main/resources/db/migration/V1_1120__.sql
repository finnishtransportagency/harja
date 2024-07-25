-- Reittipisteen etäisyys edelliseen
WITH reittipisteet AS (SELECT rp.*
                       FROM toteuman_reittipisteet
                                LEFT JOIN LATERAL UNNEST(reittipisteet) rp ON TRUE
                       WHERE toteuma = 1160),
     rp1 AS (SELECT *
             FROM reittipisteet
             WHERE aika = '2024-01-01 12:05:00.000000'),
     edellinen_rp AS (SELECT *
                      FROM reittipisteet
                      WHERE aika < (SELECT aika FROM rp1)
                      ORDER BY aika DESC
                      LIMIT 1),
     reitti AS (SELECT *
                FROM tierekisteriosoite_pisteille(
                        (SELECT sijainti FROM edellinen_rp)::geometry,
                        (SELECT sijainti FROM rp1)::geometry,
                        200))
SELECT *,
       (SELECT geometria FROM reitti)                  AS reitti,

       st_intersection(st_buffer(sijainti, 0.1),
                       (SELECT geometria FROM reitti)) AS geom
FROM rajoitusalue
WHERE st_intersects((SELECT geometria FROM reitti), rajoitusalue.sijainti)


