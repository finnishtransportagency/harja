-- Poistetaan turhaksi jäänyt versio
DROP FUNCTION IF EXISTS laskutusyhteenveto_teiden_hoito(hk_alkupvm DATE, hk_loppupvm DATE, aikavali_alkupvm DATE,
                                                        aikavali_loppupvm DATE, ur INTEGER);
