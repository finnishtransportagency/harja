ALTER TABLE paallystysilmoitus RENAME perustelu TO perustelu_tekninen_osa;
ALTER TABLE paallystysilmoitus ADD COLUMN perustelu_taloudellinen_osa VARCHAR(2048);
ALTER TABLE paallystysilmoitus RENAME kasittelyaika TO kasittelyaika_tekninen_osa;
ALTER TABLE paallystysilmoitus ADD COLUMN kasittelyaika_taloudellinen_osa date;