(ns harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.kustannussuuunnitelma-etusivu-nakyma
  "Kustannussuunnitelman etusivu määrittää, että renderöidäänkö tarjous vai kustannussuunnitelma"
  (:require [harja.tiedot.urakka.urakka :as tila]
            [harja.ui.yleiset :as yleiset ]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot :as tiedot]
            [harja.views.urakka.suunnittelu.tarjous-kustannussuunnitelma.tarjous-nakyma :as tarjous-nakyma]
            [tuck.core :as tuck]))


(defn nakyma* [e! app]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeTarjouksenTiedot)))
    (fn [e! app]
   [:div
    (when (:tarjous app)
      [:div
       [:h1 "Hoitovuoden alun tavoitehinta"]
       [:div (-> @tila/yleiset :urakka :nimi)]
       [yleiset/info-laatikko :neutraali "Tarkempi kustannusten suunnittelu tehdään tarjouksen tietojen tallentamisen jälkeen." nil nil {:sulje-nappi-id (gensym)}]
       (tarjous-nakyma/tarjous-nakyma e! app)])])))

  (defn tarjous-kustannussuunnitelma []
        (tuck/tuck tila/tarjous-kustannussuunnitelma nakyma*))
