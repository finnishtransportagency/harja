(ns harja.views.hallinta
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]

            [harja.views.toimenpidekoodit :as tp]
            [harja.ui.grid :as g]

            [clairvoyant.core :as trace :include-macros true]
            ))

(def kamat (atom [{:id :masa :nimi "Matti" :ika 42}
                  {:id :erno :nimi "Erno" :ika 99}]))

(trace/trace-forms  ;; trace start 

 (defn hallinta []
  ;; FIXME: miten hallinta valitaa, "linkkejä" vai tabeja vai jotain muuta?
  [:div
   [tp/toimenpidekoodit]
   [:hr]
   [g/grid [{:otsikko "Nimi" :nimi :nimi :tyyppi :string}
            {:otsikko "Ikä" :nimi :ika :tyyppi :numero}]
    kamat
    {:muokkaa-fn (fn [i vanha uusi]
                   (swap! kamat
                          assoc i uusi)
                   (.log js/console "kama " i ": " (pr-str vanha) " => " (pr-str uusi)))
     :muokkaustila :nappi} ; aina}
    ]]
  )


) ;; trace end
