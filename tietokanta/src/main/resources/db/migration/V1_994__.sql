--- Lisää virhe-sarake päällystysilmoitusten tallennusvirheitä varten

ALTER TABLE paallystysilmoitus
ADD COLUMN virhe TEXT,
ADD COLUMN virhe_aikaleima TIMESTAMP;
