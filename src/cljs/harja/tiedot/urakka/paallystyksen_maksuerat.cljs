(ns harja.tiedot.urakka.paallystyksen-maksuerat
  "Päällystysurakan maksuerät"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [tuck.core :as t]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; Tila
(def tila (atom {:valinnat {:urakka nil
                                 :sopimus nil
                                 :vuosi nil
                                 :tienumero nil
                                 :kohdenumero nil}
                      :maksuerat nil}))

(defonce valinnat
  (reaction
    {:urakka (:id @nav/valittu-urakka)
     :sopimus (first @u/valittu-sopimusnumero)
     :vuosi @u/valittu-urakan-vuosi
     :tienumero @yllapito-tiedot/tienumero
     :kohdenumero @yllapito-tiedot/kohdenumero}))

;; Tapahtumat

(defrecord PaivitaValinnat [valinnat])
(defrecord MaksueratHaettu [tulokset])
(defrecord MaksueratTallennettu [vastaus])

;; Tapahtumien käsittely

(extend-protocol t/Event

  PaivitaValinnat
  (process-event [{:keys [valinnat] :as e} tila]
    (update-in tila [:valinnat] merge valinnat)))