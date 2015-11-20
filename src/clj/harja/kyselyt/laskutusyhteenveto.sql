-- name: hae-laskutusyhteenvedon-tiedot
-- Hakee laskutusyhteenvetoon tarvittavat tiedot

SELECT * FROM laskutusyhteenveto(
    :hk_alkupvm::DATE, :hk_loppupvm::DATE,
    :aikavali_alkupvm::DATE, :aikavali_loppupvm::DATE,
    :urakka::INTEGER, :indeksin_nimi);


-- name: laske-asiakastyytyvaisyysbonus
-- Laskee hoitourakoissa käytettävän asiakastyytyväisyysbonuksen indeksitarkistuksen

SELECT * FROM laske_hoitokauden_asiakastyytyvaisyysbonus(
    :urakka_id,
    :maksupvm::DATE,
    :indeksinimi,
    :summa);