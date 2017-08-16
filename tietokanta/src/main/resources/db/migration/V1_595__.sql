ALTER TABLE vv_vikailmoitus DROP COLUMN "pvm";
ALTER TABLE vv_vikailmoitus RENAME COLUMN kuvaus TO "reimari-lisatiedot";
ALTER TABLE vv_vikailmoitus ALTER COLUMN "reimari-lisatiedot" SET NOT NULL;
ALTER TABLE vv_vikailmoitus ALTER COLUMN "turvalaite-id" DROP NOT NULL;
ALTER TABLE vv_vikailmoitus DROP COLUMN id;
CREATE UNIQUE INDEX VV_vikailmoitus_reimari_id_index ON vv_vikailmoitus("reimari-id");
ALTER TABLE vv_vikailmoitus ADD PRIMARY KEY USING INDEX vv_vikailmoitus_reimari_id_index;
ALTER TABLE vv_vikailmoitus ALTER COLUMN "reimari-id" SET NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-ilmoittaja" TEXT NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-turvalaitenro" TEXT NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-ilmoittajan-yhteystieto" TEXT NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-epakunnossa?" BOOLEAN NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-tyyppikoodi" TEXT NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-tilakoodi" TEXT NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-havaittu" TIMESTAMP;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-kirjattu" TIMESTAMP NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-korjattu" TIMESTAMP;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-muokattu" TIMESTAMP NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-luontiaika" TIMESTAMP NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-luoja" TEXT NOT NULL;
ALTER TABLE vv_vikailmoitus ADD COLUMN "reimari-muokkaaja" TEXT NOT NULL;

INSERT INTO integraatio (jarjestelma, nimi) VALUES ('reimari', 'hae-viat');

DROP FUNCTION IF EXISTS vv_vikailmoituksen_turvalaite_id_trigger_proc();

CREATE OR REPLACE FUNCTION vv_vikailmoituksen_turvalaite_id_trigger_proc()
  RETURNS TRIGGER AS
$$
BEGIN
   NEW."turvalaite-id" = (SELECT id FROM vv_turvalaite
                          WHERE turvalaitenro::text IS NOT NULL AND turvalaitenro::text = NEW."reimari-turvalaitenro" LIMIT 1);
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS vv_vikailmoituksen_turvalaite_id_trigger ON vv_vikailmoitus;
CREATE TRIGGER vv_vikailmoituksen_turvalaite_id_trigger
  BEFORE INSERT OR UPDATE ON vv_vikailmoitus
  FOR EACH ROW
  EXECUTE PROCEDURE vv_vikailmoituksen_turvalaite_id_trigger_proc();
