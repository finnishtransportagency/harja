-- Tiemerkinnän kustannusyhteenveto
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
  'tiemerkinnan-kustannusyhteenveto', 'Kustannusyhteenveto',
  ARRAY['urakka'::raporttikonteksti],
  ARRAY[('Aikaväli', 'aikavali', true, NULL)::raporttiparametri],
  '#''harja.palvelin.raportointi.raportit.tiemerkinnan-kustannusyhteenveto/suorita',
  ARRAY['tiemerkinta']::urakkatyyppi[]
);

-- Tiemerkinnän "muutospvm" on jatkossa vain pvm
ALTER TABLE tiemerkinnan_yksikkohintainen_toteuma RENAME COLUMN muutospvm TO paivamaara;

-- Varmistetaan, että ylläpidon sanktio on aina 0 tai positiivinen ja bonus on aina 0 tai negatiivinen
ALTER TABLE sanktio ADD CONSTRAINT yllapidon_sakko_positiivinen
CHECK (sakkoryhma != 'yllapidon_sakko' OR (sakkoryhma = 'yllapidon_sakko' AND maara >= 0));
ALTER TABLE sanktio ADD CONSTRAINT yllapidon_bonus_negatiivinen
CHECK (sakkoryhma != 'yllapidon_bonus' OR (sakkoryhma = 'yllapidon_bonus' AND maara <= 0));