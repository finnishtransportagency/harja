
-- Havainto ei liity suoraan toimenpiteeseen, vaan urakkaan, sanktio pitää sitoa toimenpiteeseen
ALTER TABLE havainto ADD COLUMN urakka integer REFERENCES urakka (id);
UPDATE havainto h SET urakka=(SELECT urakka FROM toimenpideinstanssi tpi WHERE tpi.id=h.toimenpideinstanssi);
ALTER TABLE havainto DROP COLUMN toimenpideinstanssi;

ALTER TABLE sanktio ADD COLUMN toimenpideinstanssi integer REFERENCES toimenpideinstanssi (id);

ALTER TYPE sakkoryhma RENAME TO sanktiolaji;

-- Sanktiolajin alatyypit: "puolistaattista" tietoa, eivät muutu usein, joten ei hallintaliittymää.
-- Sanktiotyyppi periaatteessa liittyisi (sanktiodokumentin mukaan) sanktiolajiin, mutta sitä ei rajoiteta.
-- Sanktiotyypin toimenpidekoodi kertoo sen, mihin urakan toimenpideinstanssiin tämä sanktio pitäisi sitoa.

CREATE TABLE sanktiotyyppi (
  id serial PRIMARY KEY,
  nimi varchar(255) NOT NULL,
  toimenpidekoodi integer REFERENCES toimenpidekoodi (id)
);


