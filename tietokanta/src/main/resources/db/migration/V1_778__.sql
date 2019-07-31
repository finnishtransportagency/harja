CREATE TABLE urakka_tavoite (
                                     id serial primary key,       -- sisäinen ID
                                     urakka integer not null references urakka(id),
                                     hoitokausi smallint not null, -- yleensä 1 - 5
                                     tavoitehinta numeric,
                                     tavoitehinta_siirretty numeric,
                                     kattohinta numeric,
                                     luotu timestamp,
                                     luoja integer references kayttaja(id),
                                     muokattu timestamp,
                                     muokkaaja integer references kayttaja(id),
                                     unique (urakka, hoitokausi));

COMMENT ON table urakka_tavoite IS
  E'Urakan tavoitteet (tavoitehinta ja kattohinta) suunnitellaan urakkatyypissä teiden-hoito (MHU).
   Urakalle määritellään hoitokausikohtainen tavoitehinta. Tavoitehinnan alitus tai ylistys vaikuttaa urakoitsijan saamaan palkkioon.
   Urakoitsija voi siirtää edellisvuoden tavoitehinnan ylityksen tai alituksen seuraavalle hoitokaudella lisättäväksi tai vähennettäväksi varsinaisesta tavoitehinnasta.
   Urakalle määritellään myös kattohinta, jota urakan kustannukset eivät missään tilanteessa ylitä. Urakoitsija vastaa kattohinnan ylittävistä kustannuksista yksin.' ;
