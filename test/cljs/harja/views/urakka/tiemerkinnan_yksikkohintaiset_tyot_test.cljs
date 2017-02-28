(ns harja.views.urakka.tiemerkinnan-yksikkohintaiset-tyot-test
  "Tiemerkinnän yks. hint. työt -näkymän testit"
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja.testutils.shared-testutils :as u]
            [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu
                                     jvh-fixture]]
            [harja.domain.tiemerkinta-toteumat :as tt]
            [reagent.core :as r]
            [harja.views.urakka.tiemerkinnan-yksikkohintaiset-tyot :as tyy]
            [harja.pvm :as pvm])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(def tiemerkinnan-toteumat
  [{:selite "Testi",
    :muutospvm nil,
    :hintatyyppi :toteuma,
    :yllapitoluokka 5,
    :id -1,
    :pituus 100,
    :yllapitokohde-id nil,
    :tr-numero 3445,
    :hinta 4}])

(def paallystyksen-kohteet [])

(def urakka
  {:id 4 :nimi "Oulun urakka"
   :urakoitsija {:nimi "YIT Rakennus Oyj" :id 2}
   :hallintayksikko {:nimi "Pohjois-Pohjanmaa" :id 9}})

(deftest yksikkohintaiset-tyot
  (komponenttitesti
    [tyy/yksikkohintaiset-tyot
     urakka
     (r/atom tiemerkinnan-toteumat)
     (r/atom paallystyksen-kohteet)]

    (is (u/sel1 [:.tiemerkinnan-yks-hint-tyot]) "Tiemerkinnän yks. hint. työt mountattu")))


