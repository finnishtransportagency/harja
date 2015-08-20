CREATE TYPE paikkausilmoituksen_tila AS ENUM ('aloitettu','valmis','lukittu');
CREATE TYPE paikkausilmoituksen_paatostyyppi AS ENUM ('hyvaksytty','hylatty');

ALTER TABLE paikkausilmoitus ADD COLUMN id serial PRIMARY KEY;
ALTER TABLE paikkausilmoitus ADD COLUMN aloituspvm date;
ALTER TABLE paikkausilmoitus ADD COLUMN valmispvm_paikkaus date;
ALTER TABLE paikkausilmoitus ADD COLUMN valmispvm_kohde date;
ALTER TABLE paikkausilmoitus ADD COLUMN luotu timestamp;
ALTER TABLE paikkausilmoitus ADD COLUMN muokattu timestamp;
ALTER TABLE paikkausilmoitus ADD COLUMN paatos paikkausilmoituksen_paatostyyppi;
ALTER TABLE paikkausilmoitus ADD COLUMN perustelu VARCHAR(2048);
ALTER TABLE paikkausilmoitus ADD COLUMN kasittelyaika timestamp without time zone;
ALTER TABLE paikkausilmoitus ADD COLUMN tila paikkausilmoituksen_tila;

ALTER TABLE paikkausilmoitus DROP COLUMN poistettu;
ALTER TABLE paikkausilmoitus ADD COLUMN poistettu boolean default false;