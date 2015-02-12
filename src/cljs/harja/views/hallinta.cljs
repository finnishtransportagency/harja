(ns harja.views.hallinta
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]

            [harja.views.toimenpidekoodit :as tp]
            [harja.ui.grid :as g]

            [clairvoyant.core :as trace :include-macros true]
            ))


(trace/trace-forms  ;; trace start 

 (defn hallinta []
  ;; FIXME: miten hallinta valitaa, "linkkejä" vai tabeja vai jotain muuta?
  [:div
   [tp/toimenpidekoodit]
   [:hr]
   ]
  )


) ;; trace end
