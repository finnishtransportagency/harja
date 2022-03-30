-- Tehdään analytiikkaportaalille oma integraatiopiste
INSERT INTO integraatio (jarjestelma, nimi) VALUES ('api', 'analytiikka-hae-toteumat');

-- Mahdollistetaan järjestelmäkäyttäjälle laajemmat käyttöoikeudet
alter table kayttaja
    add column "analytiikka-oikeus" boolean default false;
