-- name: urakan-pohjavesialueet
SELECT nimi,tunnus FROM pohjavesialueet_urakoittain WHERE urakka=:urakka;

-- name: pohjavesialueen-tiekohtaiset-summat
SELECT SUM(rp.maara) AS yhteensa,
       pva_k.tie, pva_k.alkuosa, pva_k.alkuet, pva_k.loppuosa, pva_k.loppuet,
       SUM(pva_k.pituus) AS pituus, -- Tuossa pituudessa on vain yksi arvo
       (SELECT talvisuolaraja FROM pohjavesialue_talvisuola
         WHERE pohjavesialue=:pohjavesialue
	   AND tie=pva_k.tie) AS kayttoraja
FROM suolatoteuma_reittipiste rp
  JOIN pohjavesialue_kooste pva_k ON rp.pohjavesialue = pva_k.tunnus
WHERE rp.pohjavesialue=:pohjavesialue AND rp.aika BETWEEN :alkupvm AND :loppupvm
GROUP BY pva_k.tie, pva_k.alkuosa, pva_k.alkuet, pva_k.loppuosa, pva_k.loppuet