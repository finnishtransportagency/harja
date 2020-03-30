DROP TABLE lasku_kohdistus;
DROP TABLE lasku_liite;
DROP TABLE lasku;

CREATE TABLE lasku (
                     id serial primary key,    -- sisäinen ID
                     tyyppi LASKUTYYPPI not null,
                     kokonaissumma numeric not null, -- summa voi olla nolla, mutta se ei voi olla tyhjä
                     erapaiva date not null,
                     urakka integer not null references urakka(id),
                     luotu timestamp,
                     luoja integer references kayttaja(id),
                     muokattu timestamp,
                     muokkaaja integer references kayttaja(id),
                     poistettu boolean default false,
                     laskun_numero text,
                     lisatieto text,
                     koontilaskun_kuukausi text,
                     suorittaja integer references aliurakoitsija(id));

COMMENT ON table lasku IS
  E'Pääurakoitsija laskuttaa Väylää alihankintakustannuksistaan. Laskun tiedot tallennetaan tähän tauluun. Laskuerittely ja kohdistus eri toimenpiteille, tehtäväryhmille ja tehtväille tallennetaan lasku_kohdistus-tauluun.
    Laskut kasvattavat Sampoon lähetettäviä maksueriä, mutta laskennassa käytetään lasku_kohdistus-taulun summatietoja. Useimmiten laskutettava työ kasvattaa kokonaishintaista maksuerää. Äkilliset hoitotyöt ja kolmansien osapuolten aiheuttamat vahingot
    ovat tehtäviä, jotka kasvattavat kumpikin omia maksueriään (Äkilliset hoitotyöt ja Muut).' ;

CREATE TABLE lasku_kohdistus(
                              id serial primary key,       -- sisäinen ID
                              rivi integer not null,
                              lasku integer references lasku(id),
                              summa NUMERIC not null, -- summa voi olla nolla, mutta se ei voi olla tyhjä
                              toimenpideinstanssi integer not null references toimenpideinstanssi(id),
                              tehtavaryhma integer references tehtavaryhma(id),
                              tehtava integer references toimenpidekoodi(id),
                              maksueratyyppi maksueratyyppi not null,
                              suoritus_alku timestamp,
                              suoritus_loppu timestamp,
                              luotu timestamp,
                              luoja integer references kayttaja(id),
                              muokattu timestamp,
                              muokkaaja integer references kayttaja(id),
                              lisatyo boolean default false,
                              poistettu boolean default false);

COMMENT ON table lasku_kohdistus IS
  E'Lasku-tauluun tallennettu lasku voidaan kohdistaa useaan toimenpiteeseen,tehtäväryhmään ja/tai tehtävään.
   Kohdistustiedot (käytännössä laskuerittely) tallennetaan tähän tauluun. Kohdistuksen voi tehdä eri tasoilla: toimenpide, tehtäväryhmä ja tehtävä. Vain toimenpideinstanssi-taso on aina pakollinen.' ;


CREATE TABLE lasku_liite(
                          id serial primary key,                -- sisäinen ID
                          lasku integer not null references lasku(id),
                          liite integer not null references liite(id),
                          luotu timestamp,
                          luoja integer references kayttaja(id),
                          muokattu timestamp,
                          muokkaaja integer references kayttaja(id),
                          poistettu boolean,
                          unique (lasku, liite));

COMMENT ON table lasku_liite IS
  E'Pääurakoitsija laskuttaa Väylää alihankintakustannuksistaan. Laskussa voi olla useita liitteitä. Lasku_liite-taulu linkkaa laskun ja liitteet toisiinsa.' ;


