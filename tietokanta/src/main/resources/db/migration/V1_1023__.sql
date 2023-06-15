-- Tehtävälistaan on kertynyt vuosien myötä liikaa tehtäviä joista suuri osa on käyttämättömiä.
-- Poistetaan tehtävät, jotka eivät ole käytössä (joille ei ole tehty yhtään suunnittelu- tai toteumakirjausta)
DELETE FROM tehtava WHERE id IN
                          (WITH tehtavakoodit as (select distinct toimenpidekoodi from toteuma_tehtava WHERE toimenpidekoodi is not null)
                         select t.id from tehtava t
                                    where id not in (select toimenpidekoodi from tehtavakoodit)
                                      AND t.tehtavaryhma is null
                                      AND t.id not in (select tehtava from kiinteahintainen_tyo WHERE tehtava is not null)
                                      AND t.id not in (select tehtava from kulu_kohdistus WHERE tehtava is not null)
                                      AND t.id not in (select tehtava from kustannusarvioitu_tyo WHERE tehtava is not null)
                                      AND t.id not in (select tehtava from sopimus_tehtavamaara WHERE tehtava is not null)
                                      AND t.id not in (select tehtava from toteutuneet_kustannukset WHERE tehtava is not null)
                                      AND t.id not in (select tehtava from urakka_tehtavamaara WHERE tehtava is not null) -- 747
                                      AND t.id not in (select tehtava from yksikkohintainen_tyo WHERE tehtava is not null)
                                      AND t.id not in (select tehtava from muutoshintainen_tyo WHERE tehtava is not null)
                                      AND t.id not in (select toimenpidekoodi from kan_toimenpide WHERE toimenpidekoodi is not null)
                                      AND t.id not in (select "toimenpidekoodi-id" from vv_tyo WHERE "toimenpidekoodi-id" is not null));
