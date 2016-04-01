-- kuvaus: tarkastusreitin tallennuksen tiedonkeräystaulu mobiilityökalulle
ALTER TABLE tarkastusajo ADD COLUMN paatetty timestamp;
ALTER TABLE tarkastusajo ADD COLUMN tyyppi integer NOT NULL;

CREATE TABLE tarkastusreitti (
       id integer not null,
       pistetyyppi integer not null default 0,
       tarkastusajo integer not null references tarkastusajo(id),
       aikaleima timestamp not null,
       vastaanotettu timestamp default now(),
       sijainti geometry not null,
       kitkamittaus numeric(3,2),
       liukasta boolean default false,

       PRIMARY KEY (tarkastusajo,id)
);
