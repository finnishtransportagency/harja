-- Nimi: varustetoteuma_ulkoiset_ohitus
-- Kuvaus: Listaus Tievelhon tunnisteista, jotka ohitettiin tietyn päivän tilanneteen mukaan
--
-- Tämä taulu auttaa integraatiota lataamaan vain sellaisten kohteiden tiedot, joita ei vielä ole
-- integroitu.
--
-- Kun uusi kohde tulee Velhosta tapahtuu aina joku seuraavista:
--   - kohde tallennetaan tai
--   - kohde ohitetaan tai
--   - kohteen lataus, konversio tai tallennus epäonnistuu
--
-- Lopputulos tallentuu tauluihin varustetoteuma_ulkoiset, varustetoteuma_ulkoiset_ohitus tai varustetoteuma_ulkoiset_virhe.
--
-- Integraatio ei yritä ladata jatkossa näistä tauluista löytyviä kohteita, ellei latauskerran muutospäivämäärä ole uudempi kuin näissä tauluissa

CREATE TABLE varustetoteuma_ulkoiset_ohitus
(
    velho_oid TEXT PRIMARY KEY NOT NULL,
    aikaleima timestamp        NOT NULL,
    viesti    TEXT,
    muokattu  timestamp        NOT NULL
);

CREATE INDEX varustetoteuma_ulkoiset_ohitus_muokattu ON varustetoteuma_ulkoiset_ohitus(muokattu);
