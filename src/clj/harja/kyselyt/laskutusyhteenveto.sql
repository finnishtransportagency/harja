-- name: hae-laskutusyhteenvedon-tiedot
-- Hakee laskutusyhteenvetoon tarvittavat tiedot
SELECT * FROM laskutusyhteenveto(
    :hk_alkupvm::DATE, :hk_loppupvm::DATE,
    :aikavali_alkupvm::DATE, :aikavali_loppupvm::DATE,
    :urakka::INTEGER);


-- name: laske-asiakastyytyvaisyysbonus
-- Laskee hoitourakoissa käytettävän asiakastyytyväisyysbonuksen indeksitarkistuksen
SELECT * FROM laske_hoitokauden_asiakastyytyvaisyysbonus(
    :urakka_id,
    :maksupvm::DATE,
    :indeksinimi,
    :summa);

-- name: poista-muistetut-laskutusyhteenvedot!
-- Poistaa muistetut laskutusyhteenvedot annetulle aikavälille.
-- Jos urakka on annettu, poistaa vain siltä urakalta. Muussa tapauksessa
-- poistaa kaikilta urakoilta.
DELETE FROM laskutusyhteenveto_cache
 WHERE (:urakka::INTEGER IS NULL OR urakka = :urakka) AND
       alkupvm >= :alkupvm::date AND
       loppupvm <= :loppupvm::date;

-- name: hae-yks-hint-tehtavien-maarat-aikaan-asti
SELECT
    toimenpidekoodi as tehtava_id,
    SUM(maara) as maara
FROM toteuma_tehtava tt
    JOIN toteuma t ON tt.toteuma = t.id
    JOIN urakka u ON t.urakka = u.id
WHERE urakka = :urakkaid
    AND t.paattynyt <= :loppupvm
GROUP BY toimenpidekoodi;