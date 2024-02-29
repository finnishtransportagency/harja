-- Lisätään reikäpaikkaukset tietokantaan, reikäpaikkaukset tuodaan Excelistä ja nämä insertoidaan paikkaus tauluun
CREATE TYPE paikkaustyyppi AS ENUM ('paikkaus', 'reikapaikkaus');
-- Lisätään sarake paikkaustauluun, annetaan kaikille olemassaoleville tyypiksi 'paikkaus', koska tietokannassa ei ole vielä reikäpaikkauksia
ALTER TABLE paikkaus ADD COLUMN tyyppi paikkaustyyppi DEFAULT 'paikkaus';

-- Reikäpaikkaukset ei ole kiinni missään kohteessa, joten meidän tulee sallia NULL arvot
-- Tehdään uusi constraint, sallitaan NULL jos tyyppi on reikäpaikkaus 
ALTER TABLE     paikkaus
ADD CONSTRAINT  paikkauskohde_sallitaanko_null
CHECK           (tyyppi = 'reikapaikkaus' OR paikkauskohde-id IS NOT NULL);
