-- Ilmoitustoimenpide tauluun päivitetään update lausella useammassa kohdassa lähetys id:n perusteella ja sillä
-- ei ollut indexiä. Säästämme aikaa valtavasti tällä lisäyksellä
CREATE INDEX ilmoitustoimenpide_lahetysid_index ON ilmoitustoimenpide (lahetysid);

-- Integraatiotapahtuma taulun tietoja päivitetään jokaisen integraatioviestin jälkeen. Ulkoinenid kenttä oli vailla indeksiä.
-- Ja päivitys kesti kauan. Joten lisätään indeksi. Tässä on se vaara, että insertit hidastuu, mutta nyt kaikki näyttää siltä.
-- että pieni insertin hidastuminen ei haittaa.
CREATE INDEX integraatiotapahtuma_ulkoinenid_index ON integraatiotapahtuma (ulkoinenid);
