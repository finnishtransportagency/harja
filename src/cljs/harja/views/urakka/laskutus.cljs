(ns harja.views.urakka.laskutus
  "Päätason sivu Laskutus"
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            [harja.tiedot.urakka :as u]

            [harja.loki :refer [log]]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? livi-pudotusvalikko]]
            [harja.views.urakka.laskutusyhteenveto :as laskutusyhteenveto]
            [harja.ui.komponentti :as komp]
            [harja.views.urakka.maksuerat :as maksuerat])

  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn laskutus []
  (komp/luo
    (fn []
      [:span.laskutus
       [bs/tabs {:style :tabs :classes "tabs-taso2" :active u/laskutus-valittu-valilehti}

        "Laskutusyhteenveto"
        :laskutusyhteenveto
        ^{:key "laskutusyhteenveto"}
        [laskutusyhteenveto/laskutusyhteenveto]

        "Maksuerät"
        :maksuerat
        ^{:key "maksuerat"}
        [maksuerat/maksuerat-listaus]]])))