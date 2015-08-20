CREATE TABLE yksikkohintainen_tyo (
       id serial primary key,       -- sisäinen ID
       alkupvm date,
       loppupvm date,
       maara numeric,
       yksikko varchar(64),
       yksikkohinta numeric,
       kohde integer, -- muuttunee viittaukseksi kohdeluetteloon

       tehtava INTEGER REFERENCES toimenpidekoodi (id),
       urakka integer REFERENCES urakka (id),       -- mihin urakkaan työ liittyy
       unique (urakka, tehtava, alkupvm, loppupvm)
);