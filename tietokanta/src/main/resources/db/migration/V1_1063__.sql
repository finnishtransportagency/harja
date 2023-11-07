-- Kaikki H-tehtäväryhmän tehtävät on poistettu 2024 tehtävä- ja määräluettelosta.
-- Jos tehtäväryhmille saadaan tulevaisuudessa voimassaoloaika, H-ryhmän voimassaolon voi päättää 2023 urakoihn.
UPDATE tehtava
SET voimassaolo_loppuvuosi = 2023,
    muokattu               = current_timestamp,
    muokkaaja              = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE tehtavaryhma =
      (select id from tehtavaryhma where nimi = 'Siltapäällysteet (H)');
