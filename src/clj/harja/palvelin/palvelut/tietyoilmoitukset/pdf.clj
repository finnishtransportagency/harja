(ns harja.palvelin.palvelut.tietyoilmoitukset.pdf
  "PDF-tulosteen muodostaminen tietyöilmoituksen tiedoista.
  Palauttaa XSL-FO kuvauksen hiccup muodossa."
  (:require [harja.tyokalut.xsl-fo :as xsl-fo]))

(defn tietyoilmoitus-pdf [tietyoilmoitus]
  (xsl-fo/dokumentti
   {}
   (xsl-fo/tietoja
    {}
    "Foo" "BAR")))
