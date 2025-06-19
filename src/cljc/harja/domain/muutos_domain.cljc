(ns harja.domain.muutos-domain
  "Muutos-domainin määritykset"
  (:require [clojure.spec.alpha :as s]
            [harja.domain.muokkaustiedot :as m]
            #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]]
                :cljs [[specql.impl.registry]]))
  #?(:cljs (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["mhu_muutos" ::muutos
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistettu?-sarake])


(def +muutostyypit-lomakkeella+
  "MHU muutosten mahdolliset tyypit. Näiden tulee matchata tietokannassa olevaan custom typeen MHU_MUUTOSTYYPPI"
  ["erillisrahoitettu"
   "johto-ja-hallintokorvaus"
   "maarapoikkeama"
   "pysyva"
   "toteutuneet-maarat"])

(defn tyyppi-fmt
  "Palauttaa muutostyypin tietokannasta tulevan enumin nimen käyttöliittymää varten selkokielisenä. Esim. 'pysyva' -> 'Pysyvä'."
   [tyyppi]
  ({"pysyva" "Pysyvä muutos"
    "rahavaraus" "Rahavaraus"
    "johto-ja-hallintokorvaus" "Johto- ja hallintokorvauksen muutos"
    "erillisrahoitettu" "Erillisrahoitettu"
    "toteutuneet-maarat" "Toteutuneet määrät"
    "maarapoikkeama" "Määräpoikkeama"} tyyppi))
