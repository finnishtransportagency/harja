-- name: hae-urakan-pohjavesialueiden-suolatoteumat
SELECT pv.tunnus,
       pv.nimi,
       pv.tie AS tie,
       pv.alkuosa AS alkuosa,
       pv.alkuet AS alkuet,
       pv.loppuosa AS loppuosa,
       pv.loppuet AS loppuet,
       pv.pituus AS pituus,
       rp.maara AS maara_t_per_km,
       rp.maara*pv.pituus/1000 AS yhteensa,
       ts.talvisuolaraja AS kayttoraja
FROM suolatoteuma_reittipiste rp
  INNER JOIN toteuma tot ON tot.id = rp.toteuma
  INNER JOIN pohjavesialue_kooste pv ON pv.tunnus = rp.pohjavesialue
  LEFT JOIN pohjavesialue_talvisuola ts on ts.pohjavesialue = rp.pohjavesialue
WHERE tot.urakka=:urakkaid AND rp.aika BETWEEN :alkupvm AND :loppupvm;
