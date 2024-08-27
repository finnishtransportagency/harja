(ns harja.views.urakka.laadunseuranta.talvihoitoreitit
  "Sanktioiden ja bonusten välilehti"
  (:require [tuck.core :as tuck]
    [harja.tiedot.urakka.urakka :as tila]
            [harja.views.kartta :as kartta]
            [harja.views.kartta.tasot :as kartta-tasot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.laadunseuranta.talvihoitoreitit :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]))


(defn talvihoitoreitit-sivu [e! app]
  [:div
   [kartta/kartan-paikka]
   [:h2 "Ja tänne sitte niitä juttuja"]])

(defn *talvihoitoreitit [e! app]
  (komp/luo
    (komp/sisaan-ulos #(do
                         (kartta-tasot/taso-paalle! :talvihoitoreitit)
                         (kartta-tasot/taso-paalle! :organisaatio)
                         (e! (tiedot/->HaeTalvihoitoreitit))

                         ;(reset! t-paikkauskohteet-kartalle/karttataso-nakyvissa? true)
                         (reset! nav/kartan-edellinen-koko @nav/kartan-koko)
                         (nav/vaihda-kartan-koko! :M))
      #(do
         (kartta-tasot/taso-pois! :talvihoitoreitit)))
    (fn [e! app]
      [:div.row
       [talvihoitoreitit-sivu e! app]])))

(defn talvihoitoreitit-nakyma
  []
  [tuck/tuck tila/talvihoitoreitit *talvihoitoreitit])
