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

(def +muutostyo-valinnat+ {:erillisrahoitus "Erillisrahoituksella tehtävä muutostyö"
                           :poikkeama "Poikkeaminen tehtävä- ja määräluettelon määrästä"})

;; TODO: Figma-speksissä muutostyypit ovat lomaketasolla nykyisin:
;;       Muutostyö: Sisältää erillisrahoitettu ja maarapoikkeama tyypit
;;                  erillisrahoitettu = Erillisrahoituksella tehtävä muutostyö
;;                  maarapoikkeama = Poikkeaminen tehtävä- ja määräluettelon määrästä
;;       Toteutuneet määrät taitaa olla oma asiansa nykyisin, ja se ei noudata mhu_muutos-taulun mallia kuten muut muutokset
;;       Lomakkeen muutostyypit ja niiden nimet pitää tarkistaa ja päivittää

(def +muutostyypit-lomakkeella+
  "MHU muutosten mahdolliset tyypit. Näiden tulee matchata tietokannassa olevaan custom typeen MHU_MUUTOSTYYPPI"
  ["erillisrahoitettu"
   "johto-ja-hallintokorvaus"
   "maarapoikkeama"
   "pysyva"
   "toteutuneet-maarat"
   "muutostyo"])


;; TODO: Tarkista lomakkeen valinna ja niiden nimitykset
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
    "maarapoikkeama" "Määräpoikkeama"
    "muutostyo" "Muutostyö"} tyyppi))

(defn jjh-korvaus-muutos-vai-vahennys?
  "Johto- ja hallintokorvauksen muutos on :muutos jos urakka alkanut 1.10.2024 tai aiemmin, muutoin vähennys"
  [urakan-alkupvm]
  (if (pvm/ennen? urakan-alkupvm (pvm/->pvm "5.10.2024"))
    :muutos
    :vahennys))

(defn muutos-voimassa-kesken-hoitokauden?
  "Muutoksen voimassaolo alkaa kesken hoitovuoden?"
  [voimassa-alkaen hoitovuosi]
  (pvm/valissa? voimassa-alkaen (first hoitovuosi) (second hoitovuosi)))
