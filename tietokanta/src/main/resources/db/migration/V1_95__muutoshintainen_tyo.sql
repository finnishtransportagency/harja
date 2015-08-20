CREATE TABLE muutoshintainen_tyo (
       id serial primary key,       -- sisäinen ID
       alkupvm date,
       loppupvm date,
       yksikko varchar(64),
       yksikkohinta numeric,
       
       tehtava INTEGER REFERENCES toimenpidekoodi (id),
       urakka integer REFERENCES urakka (id),       -- mihin urakkaan työ liittyy
       sopimus integer REFERENCES sopimus (id),       -- mihin sopimukseen työ liittyy
       unique (urakka, sopimus, tehtava, alkupvm, loppupvm)
);