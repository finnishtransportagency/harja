-- name: hae-laskutusyhteenvedon-tiedot
-- Hakee laskutusyhteenvetoon tarvittavat tiedot

SELECT * FROM laskutusyhteenveto(
    :hk_alkupvm::DATE, :hk_loppupvm::DATE,
    :aikavali_alkupvm::DATE, :aikavali_loppupvm::DATE,
    :urakka::INTEGER, :indeksin_nimi);