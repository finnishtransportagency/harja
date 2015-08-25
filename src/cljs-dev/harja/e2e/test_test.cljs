(ns harja.e2e.test-test
  (:require [cemerick.cljs.test :as test :refer-macros [deftest is done]]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.sillat :as sillat]
            [harja.tiedot.urakka.laadunseuranta :as urakka-laadunseuranta]
            [harja.loki :refer [log]]
            [dommy.core :refer-macros [sel sel1] :as dommy]
            [harja.e2e.testutils :as tu])
  (:require-macros [harja.e2e.macros :refer [wait-reactions]]))

(deftest ^:async e2e-testaus
  (tu/muokkaa-atomia nav/sivu :urakat)
  (tu/muokkaa-atomia u/urakan-valittu-valilehti :siltatarkastukset)
  (wait-reactions [sillat/sillat]
                 (is (= (dommy/text (aget (sel [:.grid :tr :td]) 0)) "Oulujoen silta"))
                 (is (= (dommy/text (aget (sel [:.grid :tr :td]) 1)) "902"))))

(deftest ^:async laadunseuranta
  (tu/muokkaa-atomia nav/sivu :urakat)
  (tu/muokkaa-atomia u/urakan-valittu-valilehti :laadunseuranta)
  (tu/muokkaa-atomia urakka-laadunseuranta/valittu-valilehti :tarkastukset)
  (dommy/set-value! (aget (sel [:.col-md-6 :input]) 2) "444") 
  (test/done))


