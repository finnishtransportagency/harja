CREATE TYPE tehtavaryhmatyyppi AS ENUM (
  'ylataso',
  'valitaso',
  'alataso');

CREATE TABLE tehtavaryhma (
                            id serial primary key,    -- sisäinen id
                            otsikko  text, -- korkean tason otsikko, joka kuvaa tehtäväryhmää (esim. Talvihoito)
                            nimi text not null,       -- tehtäväryhmän nimi (esim. "Viheralueiden hoito")
                            emo INTEGER REFERENCES toimenpidekoodi (id),  -- ylemmän tason tehtäväryhmä (NULL jos taso on tehtäväryhmissä ylimpänä)
                            tyyppi TEHTAVARYHMATYYPPI not null,
                            jarjestys integer, -- ryhmän järjestys käyttöliittymässä
                            nakyva boolean, -- true, jos taso näkyy käyttäjälle käyttöliittymissä
                            poistettu boolean DEFAULT false, -- true jos taso on poistettu käytöstä kaikissa urakoissa
                            luotu timestamp,
                            luoja integer REFERENCES kayttaja (id),
                            muokattu timestamp,
                            muokkaaja integer REFERENCES kayttaja (id)
);

CREATE TABLE kustannusarvioitu_tyo (
                                     id serial primary key,       -- sisäinen ID
                                     vuosi smallint not null,
                                     kuukausi smallint not null,
                                     summa numeric (7,6),
                                     tyyppi toteumatyyppi,
                                     tehtava integer REFERENCES toimenpidekoodi (id),
                                     tehtavaryhma integer REFERENCES tehtavaryhma (id),
                                     toimenpideinstanssi integer not null REFERENCES toimenpideinstanssi (id),
                                     sopimus integer not null REFERENCES sopimus(id),
                                     luotu timestamp,
                                     luoja integer references kayttaja(id),
                                     muokattu timestamp,
                                     muokkaaja integer references kayttaja(id),
                                     unique (toimenpideinstanssi, tehtava, sopimus, vuosi, kuukausi));


COMMENT ON table kustannusarvioitu_tyo IS
  E'Kustannusarvioitua työtä suunnitellaan urakkatyypissä teiden-hoito (MHU).
   Työlle suunniteltu kustannus lasketaan mukaan Sampoon lähetettävään kustannussuunnitelmaan, mutta suunniteltu summa ei kasvata Sampoon lähetettävää maksuerää (toisin kuin kokonaishintainen_tyo).
    Kustannusarvioita tehdään neljälle erityyppiselle kululle:
    - työ
    - äkillinen hoitotyö
    - kolmansien osapuolten aiheuttamat muutokset
    - muut urakan rahavaraukset' ;

