-- Taulu tiemerkintöjen korjauskustannuskille
CREATE TABLE tiemerkinta_korjauskustannukset (
    id serial primary key ,
    urakka integer references urakka (id),
    kustannusvuosi integer,
    kustannus decimal(12, 2),
    -- TODO päivitä muokkaaja
    -- pk-osuudet kustannusten yhteenlaskettu summa on oltava 100
    pk1 decimal(3,1),
    pk2 decimal(3,1),
    pk3 decimal(3,1)
);
