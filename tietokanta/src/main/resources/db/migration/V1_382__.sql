-- Välitavoiteraportti
INSERT INTO raportti (nimi, kuvaus, konteksti, parametrit, koodi, urakkatyyppi) VALUES (
 'valitavoiteraportti', 'Välitavoiteraportti',
 ARRAY['urakka'::raporttikonteksti],
 ARRAY[]::raporttiparametri[],
 '#''harja.palvelin.raportointi.raportit.valitavoiteraportti/suorita',
 'hoito'::urakkatyyppi
);