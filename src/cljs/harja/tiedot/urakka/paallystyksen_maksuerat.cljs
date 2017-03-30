(ns harja.tiedot.urakka.paallystyksen-maksuerat
  "Päällystysurakan maksuerät"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; Tila
(def muut-tyot (atom {:valinnat {:urakka nil
                                 :sopimus nil
                                 :vuosi nil
                                 :tienumero nil
                                 :kohdenumero nil}
                      :maksuerat nil}))

(defonce valinnat
  (reaction
    {:urakka (:id @nav/valittu-urakka)
     :sopimus (first @u/valittu-sopimusnumero)
     :vuosi nil ;; TODO HAE NÄMÄ
     :tienumero nil
     :kohdenumero nil))

(defrecord YhdistaValinnat [valinnat])
(defrecord MaksueratHaettu [tulokset])
(defrecord MaksueratTallennettu [vastaus])