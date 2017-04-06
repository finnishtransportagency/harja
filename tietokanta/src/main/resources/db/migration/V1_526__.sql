ALTER TABLE yllapitokohde ADD COLUMN toteutunut_hinta NUMERIC,
  ADD CONSTRAINT toteutunut_hinta_paikkauskohteella
CHECK (toteutunut_hinta IS NULL AND yllapitokohdetyotyyppi = 'paallystys' :: yllapitokohdetyotyyppi
       OR yllapitokohdetyotyyppi = 'paikkaus' :: yllapitokohdetyotyyppi);

UPDATE yllapitokohde ypk
SET toteutunut_hinta =
(SELECT toteutunut_hinta
 FROM paikkausilmoitus
 WHERE paikkauskohde = ypk.id)
WHERE ypk.yllapitokohdetyotyyppi = 'paikkaus' :: yllapitokohdetyotyyppi
      AND ypk.poistettu IS NOT TRUE;

ALTER TABLE paikkausilmoitus DROP COLUMN toteutunut_hinta;

