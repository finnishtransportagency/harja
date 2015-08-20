CREATE TABLE paikkausilmoitus (
  paikkauskohde integer REFERENCES paallystyskohde (id), -- kohteen paikkausilmoitus
  ilmoitustiedot jsonb, -- ilmoituslomakkeen tiedot
  poistettu boolean DEFAULT false
);