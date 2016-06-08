-- Laskutusyhteenvedon välimuisti
CREATE TABLE laskutusyhteenveto_cache (
  urakka integer references urakka (id),
  alkupvm DATE,
  loppupvm DATE,
  rivit laskutusyhteenveto_rivi[]
);

CREATE INDEX laskutusyhteenveto_urakka ON laskutusyhteenveto_cache (urakka);
