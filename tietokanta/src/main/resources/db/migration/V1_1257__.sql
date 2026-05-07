-- Korjataan runkokelirikkomusketehtävän suunnittelussa näkyvä yksikkö
-- Linkitetään se samalla murske-materiaalien luokkaan
UPDATE tehtava
SET suunnitteluyksikko  = 'tonni',
    materiaaliluokka_id = 6,
    muokattu = current_timestamp,
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE nimi = 'Soratien runkokelirikkokorjaukset'
  AND tehtavaryhma = (select id from tehtavaryhma where nimi = 'Q - RKR-korjaus');
