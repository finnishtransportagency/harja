CREATE UNIQUE INDEX index_paallystyskohde
ON paallystyskohde (urakka, sopimus, kohdenumero)
  WHERE poistettu = false;

CREATE UNIQUE INDEX index_paallystysilmoitus
ON paallystysilmoitus (paallystyskohde);