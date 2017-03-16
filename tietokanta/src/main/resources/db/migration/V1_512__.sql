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