-- Päivitä YHA kohdeosien geometriat ajoratatiedolla

UPDATE yllapitokohdeosa
   SET sijainti = (SELECT tierekisteriosoitteelle_viiva_ajr(
                          tr_numero, tr_alkuosa, tr_alkuetaisyys,
			  tr_loppuosa, tr_loppuetaisyys, tr_ajorata))
 WHERE yhaid IS NOT NULL;
