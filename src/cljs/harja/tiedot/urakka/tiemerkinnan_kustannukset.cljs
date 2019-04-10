(ns harja.tiedot.urakka.tiemerkinnan-kustannukset
  "Tiemerkintäurakan Kustannukset-välilehden tiedot"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.raportit :as raportit])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce kustannukset-valilehti-nakyvissa? (atom false))

(defonce raportin-parametrit
  (reaction (let [ur @nav/valittu-urakka
                  [alkupvm loppupvm] @u/valittu-hoitokauden-kuukausi
                  nakymassa? @kustannukset-valilehti-nakyvissa?]
              (when (and ur alkupvm loppupvm nakymassa?)
                (raportit/urakkaraportin-parametrit
                  (:id ur)
                  :tiemerkinnan-kustannusyhteenveto
                  {:alkupvm  alkupvm
                   :loppupvm loppupvm})))))

(defonce raportin-tiedot
  (reaction<! [parametrit @raportin-parametrit]
              {:nil-kun-haku-kaynnissa? true}
              (when parametrit
                (raportit/suorita-raportti parametrit))))
