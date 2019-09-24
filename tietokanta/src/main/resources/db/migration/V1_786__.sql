ALTER TABLE yksikkohintainen_tyo
ADD COLUMN toimenpideinstanssi INTEGER;

ALTER TABLE kokonaishintainen_tyo
ADD COLUMN luotu TIMESTAMP,
ADD COLUMN muokkaaja INTEGER references kayttaja(id),
ADD COLUMN muokattu TIMESTAMP;

ALTER TABLE kiinteahintainen_tyo
DROP CONSTRAINT kiinteahintainen_tyo_toimenpideinstanssi_sopimus_vuosi_kuuk_key,
ADD UNIQUE (toimenpideinstanssi, tehtavaryhma, tehtava, sopimus, vuosi, kuukausi);

ALTER TABLE yksikkohintainen_tyo
ADD UNIQUE (urakka, sopimus, tehtava, vuosi, kuukausi);

--  Kommenttia korjattu.
COMMENT ON table kustannusarvioitu_tyo IS
  E'Kustannusarvioitua työtä suunnitellaan urakkatyypissä teiden-hoito (MHU).
   Työlle suunniteltu kustannus lasketaan mukaan Sampoon lähetettävään kokonaishintaiseen kustannussuunnitelmaan, mutta suunniteltu summa ei kasvata Sampoon lähetettävää maksuerää (toisin kuin kokonaishintainen_tyo urakkatyypissä hoito).
    Kustannusarvioita tehdään neljälle erityyppiselle kululle:
    - työ
    - äkillinen hoitotyö
    - kolmansien osapuolten aiheuttamat vahingot
    - muut urakan rahavaraukset' ;
