-- name: hae-urakan-pohjavesialueiden-suolatoteumat
-- Hakee urakan kaikki sillat ja niiden annettuna vuonna tehdyn uusimman siltatarkastuksen
SELECT pv.tunnus,
       pv.nimi,
       sum(rp.maara) AS maara_t_per_km,
       sum(rp.maara)*sum(st_length(pv.alue))/1000 AS yhteensa,
       ts.talvisuolaraja AS kayttoraja
FROM suolatoteuma_reittipiste rp
  INNER JOIN toteuma tot ON tot.id = rp.toteuma
  INNER JOIN pohjavesialue pv ON pv.tunnus = rp.pohjavesialue
  LEFT JOIN pohjavesialue_talvisuola ts on ts.pohjavesialue = rp.pohjavesialue
WHERE rp.aika BETWEEN :alkupvm AND :loppupvm
GROUP BY pv.tunnus, pv.nimi, ts.talvisuolaraja;
