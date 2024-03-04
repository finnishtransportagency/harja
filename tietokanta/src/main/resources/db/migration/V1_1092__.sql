-- Lisätään reikäpaikkaukset tietokantaan, reikäpaikkaukset tuodaan Excelistä ja nämä insertoidaan paikkaus tauluun
CREATE TYPE paikkaustyyppi AS ENUM ('paikkaus', 'reikapaikkaus');
-- Lisätään sarake paikkaustauluun, annetaan kaikille olemassaoleville tyypiksi 'paikkaus', koska tietokannassa ei ole vielä reikäpaikkauksia
ALTER TABLE paikkaus ADD COLUMN tyyppi paikkaustyyppi DEFAULT 'paikkaus';

-- Sallitaan reikäpaikkauksille paikkauskohde NULL
ALTER TABLE paikkaus ALTER COLUMN "paikkauskohde-id" DROP NOT NULL;

-- Reikäpaikkaukset ei ole kiinni missään kohteessa, joten meidän tulee sallia NULL arvot
-- Tehdään uusi constraint, jolla sallitaan NULL jos tyyppi on reikäpaikkaus mutta muuten heitetään virhe
ALTER TABLE     paikkaus
ADD CONSTRAINT  paikkauskohde_sallitaanko_null
CHECK           (tyyppi = 'reikapaikkaus' OR paikkauskohde-id IS NOT NULL);

-- Jos ulkoinen-id ei ole 0, sen pitää olla jokaisella urakalla uniikki
-- Eli sallitaan esim arvo 123 urakalle 1 sekä 2, mutta urakalla 1 ei voi olla arvoa 123 kahdesti.
CREATE UNIQUE INDEX unique_ulkoinen_id_urakalla
ON paikkaus ("urakka-id", "ulkoinen-id")
WHERE "ulkoinen-id" <> 0;
