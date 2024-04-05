-- Lisätään reikäpaikkaukset tietokantaan, reikäpaikkaukset tuodaan Excelistä ja nämä insertoidaan paikkaus tauluun
CREATE TYPE paikkaustyyppi AS ENUM ('paikkaus', 'reikapaikkaus');
-- Lisätään sarake paikkaustauluun, annetaan kaikille olemassaoleville tyypiksi 'paikkaus', koska tietokannassa ei ole vielä reikäpaikkauksia
ALTER TABLE paikkaus ADD COLUMN "paikkaus-tyyppi" paikkaustyyppi DEFAULT 'paikkaus';

-- Sallitaan reikäpaikkauksille paikkauskohde NULL
ALTER TABLE paikkaus ALTER COLUMN "paikkauskohde-id" DROP NOT NULL;

-- Reikäpaikkaukset ei ole kiinni missään kohteessa, joten meidän tulee sallia NULL arvot
-- Tehdään uusi constraint, jolla sallitaan NULL jos tyyppi on reikäpaikkaus mutta muuten heitetään virhe
ALTER TABLE     paikkaus
ADD CONSTRAINT  paikkauskohde_sallitaanko_null
CHECK           ("paikkaus-tyyppi" = 'reikapaikkaus' OR "paikkauskohde-id" IS NOT NULL);

-- Lisätään kustannus sarake reikäpaikkauksille
ALTER TABLE paikkaus ADD COLUMN kustannus NUMERIC;

-- Vaaditaan että kustannus on läsnä reikäpaikkauksille
ALTER TABLE    paikkaus 
ADD CONSTRAINT kustannus_sallitaanko_null 
CHECK          ("paikkaus-tyyppi" = 'paikkaus' OR kustannus IS NOT NULL);

-- Lisätään paikkausmäärä sarake reikäpaikkauksille
ALTER TABLE paikkaus ADD COLUMN maara INTEGER;

-- Vaaditaan että paikkausmäärä on läsnä reikäpaikkauksille
ALTER TABLE    paikkaus 
ADD CONSTRAINT paikkaus_maara_sallitaanko_null 
CHECK          ("paikkaus-tyyppi" = 'paikkaus' OR maara IS NOT NULL);

-- Lisätään vielä yksikkö sarake reikäpaikkauksille
ALTER TABLE paikkaus ADD COLUMN "reikapaikkaus-yksikko" VARCHAR(20);

-- Vaaditaan että yksikkö on läsnä reikäpaikkauksille
ALTER TABLE    paikkaus 
ADD CONSTRAINT yksikko_sallitaanko_null 
CHECK          ("paikkaus-tyyppi" = 'paikkaus' OR "reikapaikkaus-yksikko" IS NOT NULL);

-- Jos ulkoinen-id ei ole 0, sen pitää olla jokaisella urakalla uniikki
-- Eli sallitaan esim arvo 123 urakalle 1 sekä 2, mutta urakalla 1 ei voi olla arvoa 123 kahdesti.
CREATE UNIQUE INDEX unique_ulkoinen_id_urakalla
ON paikkaus ("urakka-id", "ulkoinen-id")
WHERE "ulkoinen-id" <> 0;
