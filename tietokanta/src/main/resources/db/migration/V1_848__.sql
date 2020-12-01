CREATE TABLE toteutunut_tyo (
   id serial primary key,       -- sisäinen ID
   vuosi smallint not null CHECK (1900 < vuosi AND vuosi < 2200),
   kuukausi smallint not null CHECK (13 > kuukausi AND kuukausi > 0),
   summa numeric,
   tyyppi toteumatyyppi,
   tehtava integer REFERENCES toimenpidekoodi (id),
   tehtavaryhma integer REFERENCES tehtavaryhma (id),
   toimenpideinstanssi integer not null REFERENCES toimenpideinstanssi (id),
   sopimus integer REFERENCES sopimus(id),
   luotu timestamp,
   muokattu timestamp,
   muokkaaja integer references kayttaja(id),
   unique (toimenpideinstanssi, tehtava, sopimus, vuosi, kuukausi));


COMMENT ON table toteutunut_tyo IS
    E'Kustannusarvioitu_tyo taulusta siirretään kuukauden viimeisenä päivänä (teiden-hoito, eli mh-urakoiden) tiedot
      tähän toteutunut_tyo tauluun, josta voidaan hakea toteutuneen työn kustannukset raportteihin ja kustannuksiin.
      Toteutumia tulee neljälle erityyppiselle kululle:
      - laskutettava-tyo
      - akillinen-hoitotyo
      - muutostyo
      - muut-rahavaraukset' ;