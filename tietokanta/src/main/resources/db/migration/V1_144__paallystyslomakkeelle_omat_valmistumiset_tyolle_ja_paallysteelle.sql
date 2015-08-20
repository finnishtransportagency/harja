ALTER TABLE paallystysilmoitus RENAME valmistumispvm TO valmispvm_kohde;
ALTER TABLE paallystysilmoitus ADD COLUMN valmispvm_paallystys date;