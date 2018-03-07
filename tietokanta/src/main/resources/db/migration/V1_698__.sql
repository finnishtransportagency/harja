ALTER TABLE reimari_toimenpide
  ALTER COLUMN "reimari-id" DROP NOT NULL,
  ALTER COLUMN "reimari-luotu" DROP NOT NULL,
  ALTER COLUMN "reimari-lisatyo" DROP NOT NULL,
  ADD COLUMN "harjassa-luotu" BOOLEAN DEFAULT FALSE NOT NULL;

ALTER TABLE reimari_toimenpide
  DROP CONSTRAINT "reimari_toimenpide_reimari-sopimus_check",
  ADD CONSTRAINT "reimari_toimenpide_reimari-sopimus_check" CHECK
("reimari-sopimus" IS NULL OR (
    ("reimari-sopimus").nro IS NOT NULL AND
    sisaltaa_tekstia(("reimari-sopimus").tyyppi) AND
    sisaltaa_tekstia(("reimari-sopimus").nimi)));

ALTER TABLE reimari_toimenpide
  DROP CONSTRAINT "reimari_toimenpide_reimari-urakoitsija_check",
  ADD CONSTRAINT "reimari_toimenpide_reimari-urakoitsija_check" CHECK
("reimari-urakoitsija" IS NULL OR (("reimari-urakoitsija").id IS NOT NULL AND
                                   sisaltaa_tekstia(
                                       ("reimari-urakoitsija").nimi)));

ALTER TABLE reimari_toimenpide
  DROP CONSTRAINT "reimari_toimenpide_reimari-turvalaite_check",
  ADD CONSTRAINT "reimari_toimenpide_reimari-turvalaite_check" CHECK
("reimari-turvalaite" IS NULL OR (sisaltaa_tekstia(("reimari-turvalaite").nro) AND
                                  -- nimi saa olla tyhja
                                  ("reimari-turvalaite").ryhma IS NOT NULL));

-- ALTER TABLE reimari_toimenpide
--  DROP CONSTRAINT "reimari_toimenpide_reimari-alus_check",
--  ADD CONSTRAINT "reimari_toimenpide_reimari-alus_check" CHECK
-- ("reimari-alus" IS NULL OR (sisaltaa_tekstia(("reimari-alus").tunnus)));

-- ALTER TABLE reimari_toimenpide
--  DROP CONSTRAINT "reimari_toimenpide_reimari-vayla_check",
--  ADD CONSTRAINT "reimari_toimenpide_reimari-vayla_check" CHECK
-- ("reimari-vayla" IS NULL OR (sisaltaa_tekstia(("reimari-vayla").nro)));

ALTER TABLE reimari_toimenpide
  DROP CONSTRAINT "reimari_toimenpide_reimari-tila_check",
  ADD CONSTRAINT "reimari_toimenpide_reimari-tila_check" CHECK
("reimari-tila" IS NULL OR (sisaltaa_tekstia("reimari-tila")));

ALTER TABLE reimari_toimenpide
  DROP CONSTRAINT "reimari_toimenpide_reimari-tyoluokka_check",
  ADD CONSTRAINT "reimari_toimenpide_reimari-tyoluokka_check" CHECK
("reimari-tyoluokka" IS NULL OR (sisaltaa_tekstia("reimari-tyoluokka")));

ALTER TABLE reimari_toimenpide
  DROP CONSTRAINT "reimari_toimenpide_reimari-tyolaji_check",
  ADD CONSTRAINT "reimari_toimenpide_reimari-tyolaji_check" CHECK
("reimari-tyolaji" IS NULL OR (sisaltaa_tekstia("reimari-tyolaji")));

ALTER TABLE reimari_toimenpide
  DROP CONSTRAINT "reimari_toimenpide_reimari-tyyppi_check",
  ADD CONSTRAINT "reimari_toimenpide_reimari-toimenpidetyyppi_check" CHECK
("reimari-toimenpidetyyppi" IS NULL OR (sisaltaa_tekstia("reimari-toimenpidetyyppi")));