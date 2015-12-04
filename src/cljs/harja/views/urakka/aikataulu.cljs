(ns harja.views.urakka.aikataulu
  "Ylläpidon urakoiden aikataulunäkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.valitavoitteet :as vt]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as y]
            [harja.pvm :as pvm]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.fmt :as fmt]
            [cljs-time.core :as t]
            [harja.domain.roolit :as roolit]
            [cljs.core.async :refer [<!]])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))



(defn aikataulu
  "Urakan välitavoitteet näkymä. Ottaa parametrinä urakan ja hakee välitavoitteet sille."
  []
  (let [_ 2]
    (komp/luo
      (fn []
        [:div.aikataulu
         [grid/grid
          {:otsikko "Kohteiden aikataulu"}

          [{:otsikko "YHA-ID" :leveys "15%" :nimi :yha-id :tyyppi :string :pituus-max 128 :muokattava? (constantly false)}
           {:otsikko "Kohde" :leveys "60%" :nimi :kohde :tyyppi :string :muokattava? (constantly false)}
           {:otsikko "Alkamispvm" :leveys "20%" :tyyppi :string :muokattava? (constantly false)
            :nimi    :tila}]
          ;; FIXME: kohteiden tavoite aikataulut jne.
          [{:yha-id 1 :kohde "Mt 22 Ruohonjuuren pätkä" :tila "Kaikki valmiina"}
           {:yha-id 2 :kohde "Mt 22 Terilän silta" :tila "Kaikki valmiina"}
           {:yha-id 3 :kohde "Mt 22 Matulan  pätkä" :tila "Kohde kesken"}
           {:yha-id 4 :kohde "Mt 22 koskenlaskijan kuru" :tila "Kohde kesken"}
           {:yha-id 5 :kohde "Mt 22 rampit" :tila "Kaikki valmiina"}
           ]]]))))