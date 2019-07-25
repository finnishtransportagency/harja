CREATE TABLE aliurakoitsija(
      id serial primary key,       -- sisäinen ID
      nimi text not null,
      ytunnus text,
      luotu timestamp,
      luoja integer references kayttaja(id),
      muokattu timestamp,
      muokkaaja integer references kayttaja(id),
      poistettu boolean,
      unique (nimi));

COMMENT ON table aliurakoitsija IS
  E'Pääurakoitsija laskuttaa Väylää alihankintakustannuksistaan. Aliurakoitsijoiden tiedot tallennetaan tähän tauluun.
   Aliurakoitsijalistaa kasvatetaan laskutustietojen perusteella.' ;

CREATE TABLE lasku (
         id serial primary key,    -- sisäinen ID
         viite text not null,      -- viite tai muu ulkoisen järjestelmän tunniste laskulle
         erapaiva date not null,
         summa numeric not null,
         urakka integer not null references urakka(id),
         suorittaja integer references alihankkija(id),
         luotu timestamp,
         luoja integer references kayttaja(id),
         muokattu timestamp,
         muokkaaja integer references kayttaja(id),
         poistettu boolean,
         unique (viite));

COMMENT ON table lasku IS
  E'Pääurakoitsija laskuttaa Väylää alihankintakustannuksistaan. Laskun tiedot tallennetaan tähän tauluun. Summat kasvattavat
   Sampoon lähetettäviä maksueriä. Useimmiten laskutettava työ kasvattaa kokonaishintaista maksuerää. Äkilliset hoitotyöt ja kolmansien osapuolten aiheuttamat vahingot
   ovat tehtäviä, jotka kasvattavat kumpikin omia maksueriään (Äkilliset hoitotyöt ja Muut).' ;

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

CREATE TABLE lasku_kohdistus(
       id serial primary key,       -- sisäinen ID
       lasku integer references lasku(id),
       toimenpideinstanssi integer not null references toimenpideinstanssi(id),
       tehtavaryhma integer references tehtavaryhma(id),
       tehtava integer references toimenpidekoodi(id),
       luotu timestamp,
       luoja integer references kayttaja(id),
       muokattu timestamp,
       muokkaaja integer references kayttaja(id),
       unique (lasku, toimenpideinstanssi, tehtavaryhma, tehtava));

COMMENT ON table lasku_kohdistus IS
  E'Lasku-tauluun tallennettu lasku voidaan kohdistaa useaan toimenpiteeseen,tehtäväryhmään ja/tai tehtävään.
   Kohdistustiedot tallennetaan tähän tauluun. Kohdistuksen voi tehdä eri tasoilla: toimenpide, tehtäväryhmä ja tehtävä. Vain toimenpideinstanssi-taso on aina pakollinen.' ;

