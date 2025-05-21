(ns harja.domain.muutos-domain)

(defn tyyppi-fmt
  "Palauttaa muutostyypin tietokannasta tulevan enumin nimen käyttöliittymää varten selkokielisenä. Esim. 'pysyva' -> 'Pysyvä'."
   [tyyppi]
  ({"pysyva" "Pysyvä"
    "rahavaraus" "Rahavaraus"
    "johto-ja-hallintokorvaus" "Johto- ja hallintokorvaus"
    "erillisrahoitettu" "Erillisrahoitettu"
    "toteutuneet-maarat" "Toteutuneet määrät"
    "maarapoikkeama" "Määräpoikkeama"} tyyppi))
