(ns harja.domain.muutos-domain)

(def +muutostyypit+
  "MHU muutosten mahdolliset tyypit. Näiden tulee matchata tietokannassa olevaan custom typeen MHU_MUUTOSTYYPPI"
  #{"pysyva"
    "rahavaraus"
    "johto-ja-hallintokorvaus"
    "erillisrahoitettu"
    "toteutuneet-maarat"
    "maarapoikkeama"})

(defn tyyppi-fmt
  "Palauttaa muutostyypin tietokannasta tulevan enumin nimen käyttöliittymää varten selkokielisenä. Esim. 'pysyva' -> 'Pysyvä'."
   [tyyppi]
  ({"pysyva" "Pysyvä"
    "rahavaraus" "Rahavaraus"
    "johto-ja-hallintokorvaus" "Johto- ja hallintokorvaus"
    "erillisrahoitettu" "Erillisrahoitettu"
    "toteutuneet-maarat" "Toteutuneet määrät"
    "maarapoikkeama" "Määräpoikkeama"} tyyppi))
