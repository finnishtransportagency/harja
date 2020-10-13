(ns harja.df-data
  (:require [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as urakka])
  (:require-macros [reagent.ratom :refer [reaction]]))

(defonce data
         (reaction {:harja.tiedot.navigaatio    {:valittu-urakka @nav/valittu-urakka}
                    :harja.tiedot.urakka.urakka {:yleiset                          @urakka/yleiset
                                                 :suunnittelu-tehtavat             @urakka/suunnittelu-tehtavat
                                                 :suunnittelu-kustannussuunnitelma @urakka/suunnittelu-kustannussuunnitelma}}))
