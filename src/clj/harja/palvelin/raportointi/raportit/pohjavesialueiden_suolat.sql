-- name: urakan-pohjavesialueet
SELECT nimi,tunnus FROM pohjavesialueet_urakoittain WHERE urakka=:urakka;

-- name: pohjavesialueen-tiekohtaiset-summat
SELECT SUM(rp.maara) AS yhteensa, 
       rp.tie, rp.alkuosa, rp.alkuet, rp.loppuosa, rp.loppuet,
       (SELECT SUM(pituus) FROM pohjavesialue_kooste
         WHERE tunnus=:pohjavesialue
	   AND tie=rp.tie
	   AND alkuosa=rp.alkuosa
	   AND alkuet=rp.alkuet
	   AND loppuosa=rp.loppuosa
	   AND loppuet=rp.loppuet) AS pituus,
       (SELECT talvisuolaraja FROM pohjavesialue_talvisuola
         WHERE pohjavesialue=:pohjavesialue
	   AND tie=rp.tie) AS kayttoraja
FROM suolatoteuma_reittipiste rp
WHERE rp.pohjavesialue=:pohjavesialue AND rp.aika BETWEEN :alkupvm AND :loppupvm
GROUP BY rp.tie, rp.alkuosa, rp.alkuet, rp.loppuosa, rp.loppuet;
