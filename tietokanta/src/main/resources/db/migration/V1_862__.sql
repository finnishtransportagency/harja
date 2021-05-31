-- Kustannussuunnitelmassa on tallennettu hoidonjohtopalkkioita väärällä tehtävällä, niin oikaistaan ne. Näitä ei tallenneta muualta käsin tähän tauluun, niin pitäisi olla safe.
update kustannusarvioitu_tyo
  set tehtava = (select id from toimenpidekoodi tpk where tpk.nimi = 'Hoidonjohtopalkkio')
  where tehtava = (select id from toimenpidekoodi tpk where tpk.yksiloiva_tunniste = 'c9712637-fbec-4fbd-ac13-620b5619c744'); -- Hoitourakan työnjohto -toimenpidekoodin tunniste

-- Bugin seurauksena syntyneitä rivejä siivotaan pois
delete from kustannusarvioitu_tyo
  where id in
    (select id from kustannusarvioitu_tyo
      where toimenpideinstanssi in (select id from toimenpideinstanssi where toimenpide = 601)
        and tehtavaryhma is null and tehtava is null);
