(ns harja.df-data
  (:require [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as urakka]
            [harja.tiedot.urakka.tienumerot-kartalla :as tienumerot-kartalla]
            )
  (:require-macros [reagent.ratom :refer [reaction]]))

(def data
         (reaction {:harja.tiedot.navigaatio    {:valittu-urakka @nav/valittu-urakka
                                                 :valittu-ilmoitus-id @nav/valittu-ilmoitus-id
                                                 :kartalla-nakyva-alue @nav/kartalla-nakyva-alue}
                    :harja.tiedot.urakka.tienumerot-kartallla {:tienumerot @tienumerot-kartalla/tienumerot
                                                               :tienumerot-kartalla @tienumerot-kartalla/tienumerot-kartalla}
                    :harja.tiedot.urakka.urakka {:yleiset                          @urakka/yleiset
                                                 :suunnittelu-tehtavat             @urakka/suunnittelu-tehtavat
                                                 :suunnittelu-kustannussuunnitelma @urakka/suunnittelu-kustannussuunnitelma
                                                 :kustannusten-seuranta            @urakka/kustannusten-seuranta}}))
