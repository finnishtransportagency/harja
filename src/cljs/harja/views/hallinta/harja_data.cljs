(ns harja.views.hallinta.harja-data
  (:require [harja.ui.bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.views.hallinta.harja-data.diagrammit :as diagrammit]
            [harja.views.hallinta.harja-data.analyysi :as analyysi]
            [harja.ui.komponentti :as komp]))

(defn harja-data
  []
  (komp/luo
    (fn []
      [bs/tabs {:style :tabs :classes "tabs-taso2"
                :active (nav/valittu-valilehti-atom :data)}
        "Diagrammit"
        :diagrammit
        (when true ;;TODO oikeudet
          ^{:key "diagrammit"}
          [diagrammit/diagrammit])

        "Analyysi"
        :analyysi
        (when true ;;TODO oikeudet
          ^{:key "analyysi"}
          [analyysi/analyysi])])))
