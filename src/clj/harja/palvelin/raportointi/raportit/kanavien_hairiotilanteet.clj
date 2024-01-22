(ns harja.palvelin.raportointi.raportit.kanavien-hairiotilanteet
  "Häiriötilanne raportti"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [clojure.string :as str]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]))

(defn suorita [_ _ {:keys [parametrit]}]

  (let [{:keys [tiedot urakka]} parametrit
        otsikko "T"]

    [:raportti {:nimi otsikko
                :piilota-otsikko? true}
     
                ]))
