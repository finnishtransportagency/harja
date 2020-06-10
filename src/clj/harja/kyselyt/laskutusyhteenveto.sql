-- name: hae-laskutusyhteenvedon-tiedot
-- Hakee laskutusyhteenvetoon tarvittavat tiedot
SELECT * FROM laskutusyhteenveto(
    :hk_alkupvm::DATE, :hk_loppupvm::DATE,
    :aikavali_alkupvm::DATE, :aikavali_loppupvm::DATE,
    :urakka::INTEGER);

-- name: hae-laskutusyhteenvedon-tiedot-teiden-hoito
-- Hakee laskutusyhteenvetoon tarvittavat tiedot teiden hoidon urakassa (MHU)
SELECT * FROM mhu_laskutusyhteenveto_teiden_hoito(
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

-- name: poista-urakan-kaikki-muistetut-laskutusyhteenvedot!
-- Poistaa kaikki urakan muistetut laskutusyhteenvedot.
DELETE FROM laskutusyhteenveto_cache WHERE urakka = :urakka


-- name: hae-urakat-joille-laskutusyhteenveto-voidaan-tehda
-- Hakee käynnissä olevat urakat, joille voidaan laskea valmiiksi laskutyshteenveto
-- annetulle aikavälille.
SELECT u.id, u.nimi, u.tyyppi
  FROM urakka u
 WHERE u.tyyppi IN ('hoito'::urakkatyyppi, 'teiden-hoito'::urakkatyyppi) AND
       u.alkupvm < NOW() AND
       u.loppupvm > (date_trunc('month',NOW()) - '2 months'::interval) AND
       NOT EXISTS (SELECT rivit
                     FROM laskutusyhteenveto_cache l
		    WHERE l.urakka = u.id AND l.alkupvm = :alku::DATE AND l.loppupvm = :loppu::DATE);

-- name: hoitokautta-edeltavan-syyskuun-indeksikorotus
SELECT * FROM laske_kuukauden_indeksikorotus (:hoitokauden-alkuvuosi::INTEGER,9::INTEGER, :indeksinimi::VARCHAR,:summa::NUMERIC, :perusluku::NUMERIC);

-- name: hoitokauden-suolasakko
SELECT * FROM hoitokauden_suolasakko(:urakka-id, :hoitokauden_alkupvm::DATE, :hoitokauden_loppupvm::DATE);