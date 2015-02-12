(ns harja.views.hallinta
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]

            [harja.views.toimenpidekoodit :as tp]
            [harja.ui.grid :as g]
            ))


(defn hallinta []
  ;; FIXME: miten hallinta valitaa, "linkkejä" vai tabeja vai jotain muuta?
  [:div
   [tp/toimenpidekoodit]
   [:hr]
   ]
  )

