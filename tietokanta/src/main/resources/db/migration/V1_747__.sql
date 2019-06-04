CREATE MATERIALIZED VIEW pohjavesialue_kooste AS (SELECT nimi, tunnus, alue, pituus, tie, alkuosa, alkuet, loppuosa, loppuet FROM (SELECT array_agg(id) AS id, 
       (array_agg(nimi))[1] AS nimi, 
       (array_agg(tunnus))[1] AS tunnus, 
       st_linemerge(st_collect(alue)) AS alue,
       st_length(st_collect(alue)) AS pituus,
       min(tr_numero) AS tie,
       min(tr_alkuosa) AS alkuosa,
       min(tr_alkuetaisyys) AS alkuet,
       max(tr_loppuosa) AS loppuosa,
       max(tr_loppuetaisyys) AS loppuet
 FROM pohjavesialue
 GROUP BY tr_numero, tr_alkuosa) AS q);
