(ns harja.views.kanavat.hallinta.huoltokohteiden-hallinta
  (:require [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.hallinta.huoltokohteiden-hallinta :as tiedot]
            [harja.ui.komponentti :as komp]))

(defn kohteiden-liittaminen* [e! app]
  (komp/luo
    (komp/sisaan-ulos #(e! (tiedot/->Nakymassa? true))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! app]
      [:div "WIP"])))

(defn hallinta []
  [tuck tiedot/tila kohteiden-liittaminen*])
