-- Poista päällystysilmoitukselta aikataulutiedot (paitsi takuupvm), nämä saadaan suoraan ylläpitokohteen aikataulutiedoista

ALTER TABLE paallystysilmoitus DROP COLUMN aloituspvm;
ALTER TABLE paallystysilmoitus DROP COLUMN valmispvm_kohde;
ALTER TABLE paallystysilmoitus DROP COLUMN valmispvm_paallystys;