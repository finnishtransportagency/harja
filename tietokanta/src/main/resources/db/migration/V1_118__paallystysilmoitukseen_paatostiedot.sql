CREATE TYPE paallystysilmoituksen_paatostyyppi AS ENUM ('hyvaksytty','hylatty');
ALTER table paallystysilmoitus ADD COLUMN paatos paallystysilmoituksen_paatostyyppi;
ALTER table paallystysilmoitus ADD COLUMN perustelu VARCHAR(256);
ALTER TABLE paallystysilmoitus ADD COLUMN kasittelyaika timestamp without time zone;