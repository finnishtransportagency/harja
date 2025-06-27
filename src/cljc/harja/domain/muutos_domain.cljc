(ns harja.domain.muutos-domain
  "Muutos-domainin määritykset"
  (:require [clojure.spec.alpha :as s]
            [harja.pvm :as pvm]
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
   [tyyppi urakan-sopimustyyppi]
  ({"pysyva" "Pysyvä muutos"
    "rahavaraus" "Rahavaraus"
    "johto-ja-hallintokorvaus" (if (= :mhu+ urakan-sopimustyyppi)
                                 "Kumppanuusmaksun muutos"
                                 "Johto- ja hallintokorvauksen muutos")
    "erillisrahoitettu" "Erillisrahoitettu"
    "toteutuneet-maarat" "Toteutuneet määrät"
    "maarapoikkeama" "Määräpoikkeama"} tyyppi))

(defn jjh-korvaus-muutos-vai-vahennys?
  "Johto- ja hallintokorvauksen muutos on :muutos jos urakka alkanut 1.10.2024 tai aiemmin, muutoin vähennys"
  [urakan-alkupvm]
  (if (pvm/ennen? urakan-alkupvm (pvm/->pvm "5.10.2024"))
    :muutos
    :vahennys))
