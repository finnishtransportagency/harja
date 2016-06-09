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
DELETE
  FROM laskutusyhteenveto_cache
 WHERE (:urakka IS NULL OR urakka = :urakka) AND
       alkupvm >= :alkupvm AND
       loppupvm <= :loppupvm
