-- ** KORJAA ALUSLAJI HINT -> HIN**
-- Edellisessä migraatiossa lisättiin 'HIN' aluslaji
-- Päivitetään rivit käyttämään sitä..
UPDATE kan_liikennetapahtuma_alus
SET laji = 'HIN'
WHERE laji = 'HINT';

ALTER TYPE LIIKENNETAPAHTUMA_ALUSLAJI
RENAME TO _aluslaji;

-- Luodaan uusi enum, jossa ei ole typotettua aluslajia
CREATE TYPE LIIKENNETAPAHTUMA_ALUSLAJI AS ENUM ('RAH', 'MAT', 'ÖLJ', 'HIN', 'HUV', 'PRO', 'SEK', 'LAU');

ALTER TABLE kan_liikennetapahtuma_alus
  RENAME laji TO _laji;
ALTER TABLE kan_liikennetapahtuma_alus
  ADD COLUMN laji LIIKENNETAPAHTUMA_ALUSLAJI;

UPDATE kan_liikennetapahtuma_alus
SET laji = _laji :: TEXT :: LIIKENNETAPAHTUMA_ALUSLAJI;

ALTER TABLE kan_liikennetapahtuma_alus
  DROP COLUMN _laji;

-- Pudotetaan vanha
DROP TYPE _ALUSLAJI;

-- ** NIPPU EI OLE OMA TAULUNSA, VAAN OPTIONAALINEN TIETO ALUKSELLA **
-- Poistetaan nipputaulu ja siirretään nipun lukumäärä alusrivin tietoihin
DROP TABLE kan_liikennetapahtuma_nippu;

ALTER TABLE kan_liikennetapahtuma_alus
  ADD COLUMN nippulkm INTEGER;

ALTER TABLE kan_liikennetapahtuma
  DROP CONSTRAINT vain_itsepalvelua_enemman_kuin_yksi;

-- ** Sillan avaus ja sulun käyttäminen tarvitsevat omat palvelumuotonsa ja lukumääränsä **
ALTER TABLE kan_liikennetapahtuma
  ADD COLUMN "silta-avaus" BOOLEAN,
  ADD COLUMN "silta-palvelumuoto" LIIKENNETAPAHTUMA_PALVELUMUOTO,
  ADD COLUMN "silta-lkm" INTEGER
  CONSTRAINT silta_vain_itsepalvelua_enemman_kuin_yksi CHECK
  ("silta-lkm" = 1 OR "silta-palvelumuoto" = 'itse' OR "silta-palvelumuoto" IS NULL);

-- Luodaan uusi enum, jossa ei ole sillan avausta
ALTER TYPE LIIKENNETAPAHTUMA_TOIMENPIDETYYPPI
RENAME TO _tp;
CREATE TYPE SULUTUS_TOIMENPIDETYYPPI AS ENUM ('sulutus', 'tyhjennys');

-- Lisätään sulutukselle tyyppi- ja lukumääräsarakkeet
ALTER TABLE kan_liikennetapahtuma
  RENAME COLUMN toimenpide TO _toimenpide;

ALTER TABLE kan_liikennetapahtuma
  ADD COLUMN "sulku-toimenpide" SULUTUS_TOIMENPIDETYYPPI,
  ADD COLUMN "sulku-palvelumuoto" LIIKENNETAPAHTUMA_PALVELUMUOTO,
  ADD COLUMN "sulku-lkm" INTEGER
  CONSTRAINT sulku_vain_itsepalvelua_enemman_kuin_yksi CHECK
  ("sulku-lkm" = 1 OR "sulku-palvelumuoto" = 'itse' OR "sulku-palvelumuoto" IS NULL);

-- Päivitetään rivit, joissa on tehty sulutusta tai tyhjennystä
UPDATE kan_liikennetapahtuma
SET "sulku-toimenpide" =
CASE WHEN (_toimenpide :: TEXT = 'sulutus' OR _toimenpide :: TEXT = 'tyhjennys')
  THEN _toimenpide :: TEXT :: SULUTUS_TOIMENPIDETYYPPI END;

-- Päivitetään rivit, joissa on tehty sillan avaus
UPDATE kan_liikennetapahtuma
SET "silta-avaus" =
CASE WHEN (_toimenpide :: TEXT = 'sillan-avaus')
  THEN TRUE END;

-- Asetetaan oletuslukumääriksi 1
UPDATE kan_liikennetapahtuma
SET "sulku-lkm" = 1
WHERE "sulku-toimenpide" IS NOT NULL;

UPDATE kan_liikennetapahtuma
SET "silta-lkm" = 1
WHERE "silta-avaus" IS NOT NULL;

-- Poistetaan vanhat sarakkeet ja tiedot
ALTER TABLE kan_liikennetapahtuma
  DROP COLUMN _toimenpide;
DROP TYPE _TP;

-- Päivitetään triggeriä
CREATE OR REPLACE FUNCTION toimenpide_kohteen_mukaan_proc()
  RETURNS TRIGGER AS
$$
DECLARE kohteen_tyyppi KOHTEEN_TYYPPI;
BEGIN
  kohteen_tyyppi := (SELECT kan_kohde.tyyppi
                     FROM kan_kohde
                     WHERE id = NEW."kohde-id");
  -- jos kohteen tyyppi on silta, tapahtumalla ei saa olla sulkutietoja
  IF (kohteen_tyyppi = 'silta')
  THEN
    IF (NEW."sulku-toimenpide" IS NULL AND
        NEW."sulku-palvelumuoto" IS NULL AND
        NEW."sulku-lkm" IS NULL AND
        NEW."silta-avaus" IS NOT NULL AND
        NEW."silta-lkm" IS NOT NULL AND
        NEW."silta-palvelumuoto" IS NOT NULL)
    THEN
      RETURN NEW;
    ELSE
      RAISE EXCEPTION 'Yritettiin luoda siltakohteelle tapahtuma, joka sisältää sulun tietoja, tai ei sisällä kaikkia sillan tietoja';
      RETURN NULL;
    END IF;
    -- jos kohteen tyyppi on sulku, tapahtumalla ei saa olla siltatietoja
  ELSIF (kohteen_tyyppi = 'sulku')
    THEN
      IF (NEW."sulku-toimenpide" IS NOT NULL AND
          NEW."sulku-palvelumuoto" IS NOT NULL AND
          NEW."sulku-lkm" IS NOT NULL AND
          NEW."silta-avaus" IS NULL AND
          NEW."silta-lkm" IS NULL AND
          NEW."silta-palvelumuoto" IS NULL)
      THEN
        RETURN NEW;
      ELSE
        RAISE EXCEPTION 'Yritettiin luoda sulkukohteelle tapahtuma, joka sisältää sillan tietoja, tai ei sisällä kaikkia sulun tietoja';
        RETURN NULL;
      END IF;
      -- jos kohde on yhdistetty kohde..
  ELSIF (kohteen_tyyppi = 'sulku-ja-silta')
    THEN
      IF (
        -- tapahtumalla on VAIN sillan tiedot..
        (NEW."sulku-toimenpide" IS NULL AND
         NEW."sulku-palvelumuoto" IS NULL AND
         NEW."sulku-lkm" IS NULL AND
         NEW."silta-avaus" IS NOT NULL AND
         NEW."silta-lkm" IS NOT NULL AND
         NEW."silta-palvelumuoto" IS NOT NULL) OR
        -- tai tapahtumalla on VAIN sulun tiedot..
        (NEW."sulku-toimenpide" IS NOT NULL AND
         NEW."sulku-palvelumuoto" IS NOT NULL AND
         NEW."sulku-lkm" IS NOT NULL AND
         NEW."silta-avaus" IS NULL AND
         NEW."silta-lkm" IS NULL AND
         NEW."silta-palvelumuoto" IS NULL) OR
        -- tai tapahtumalla on KAIKKI tiedot
        (NEW."sulku-toimenpide" IS NOT NULL AND
         NEW."sulku-palvelumuoto" IS NOT NULL AND
         NEW."sulku-lkm" IS NOT NULL AND
         NEW."silta-avaus" IS NOT NULL AND
         NEW."silta-lkm" IS NOT NULL AND
         NEW."silta-palvelumuoto" IS NOT NULL))
      THEN
        RETURN NEW;
      ELSE
        RAISE EXCEPTION 'Yritettiin luoda yhdistetylle kohteelle tapahtuma, joka ei sisällä kaikkia sulun tai sillan tietoja';
        RETURN NULL;
      END IF;
  END IF;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS toimenpide_kohteen_mukaan_trigger
ON kan_liikennetapahtuma;