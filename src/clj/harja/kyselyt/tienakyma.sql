-- name: hae-toteumat
-- Hakee kaikki toteumat
SELECT t.id,
       t.tyyppi,
       t.reitti,
       t.alkanut, t.paattynyt,
       tt.toimenpidekoodi AS tehtava_toimenpidekoodi,
       tpk.nimi AS tehtava_toimenpide,
       rp.sijainti AS reittipiste_sijainti,
       rp.aika AS reittipiste_aika
  FROM toteuma t
  JOIN toteuma_tehtava tt ON tt.toteuma = t.id
  JOIN toimenpidekoodi tpk ON tt.toimenpidekoodi = tpk.id
  JOIN reittipiste rp ON t.id = rp.toteuma
 WHERE ST_Intersects(t.reitti, :sijainti)
   AND ((t.alkanut BETWEEN :alku AND :loppu) OR
        (t.paattynyt BETWEEN :alku AND :loppu))

-- name: hae-tarkastukset
SELECT t.id, t.aika, t.tyyppi, t.tarkastaja,
       t.havainnot, t.laadunalitus,
       t.sijainti,
       CASE WHEN o.tyyppi = 'urakoitsija' :: organisaatiotyyppi
       THEN 'urakoitsija' :: osapuoli
       ELSE 'tilaaja' :: osapuoli
       END AS tekija
FROM tarkastus t
     JOIN kayttaja k ON t.luoja = k.id
     JOIN organisaatio o ON o.id = k.organisaatio
WHERE sijainti IS NOT NULL AND
      ST_Intersects(t.sijainti, :sijainti) AND
      (t.aika BETWEEN :alku AND :loppu)
