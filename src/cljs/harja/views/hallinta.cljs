(ns harja.views.hallinta
  "Päätason sivu Hallinta, josta kaikkeen ylläpitötyöhön pääsee käsiksi."
  (:require [reagent.core :refer [atom] :as reagent]
            [bootstrap :as bs]
            
            [harja.views.toimenpidekoodit :as tp]
            [harja.views.indeksit :as i]
            [harja.ui.grid :as g]
            ))


(defn hallinta []
  ;; FIXME: miten hallinta valitaa, "linkkejä" vai tabeja vai jotain muuta?

   [bs/tabs {}
   
   ;; todo käännä toisin päin kun indeksit valmis
      "Indeksit"
   ^{:key "indeksit"}
   [i/indeksit-elementti]
   
   "Tehtävät"
   ^{:key "tehtävät"}
   [tp/toimenpidekoodit]])

