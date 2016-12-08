(ns harja.views.kartta.infopaneeli-test
  (:require  [cljs.test :as t :refer-macros [deftest is testing async]]
             [reagent.core :as r]
             [harja.loki :refer [log tarkkaile! error]]
             [harja.views.kartta.infopaneeli :as sut]
             [harja.testutils :as u])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(def testidata
  {:haetaan? true
   :koordinaatti [20 20]
   :asiat [{:otsikko "Toimenpidepyyntö 20.12.2016 15:55:15"
            :tiedot [{:otsikko "Ilmoitettu" :tyyppi :pvm-aika :nimi :ilmoitettu}
                     {:otsikko "Kuittaukset" :tyyppi :positiivinen-numero :hae #(constantly 5)}]
            :data {:ilmoitettu (harja.pvm/nyt)}}
           {:otsikko "Auraus 15km"
            :tiedot [{:otsikko "Hyvää työtä" :tyyppi :radio :nimi :hyvaa-tyota?}
                     {:otsikko "Toimenpide" :tyyppi :tierekisteriosoite :nimi :tr }]
            :data {:hyvaa-tyota? true
                   :tr {:numero 20 :alkuosa 1 :alkuetaisyys 1 :loppuosa 2 :loppuetaisyys 200}}}]})
(deftest otsikot
  (let [asiat-atomi (r/atom testidata)
        piilota-fn! #(log "piilota-fn! kutsuttu")
        linkkifunktiot (r/atom nil)]
    (komponenttitesti
     [sut/infopaneeli asiat-atomi piilota-fn! linkkifunktiot]

     "infopaneelin sulkunappi ja otsikot, ei tietojen kenttiä"
     (is (= 1 (count (u/sel [:#kartan-infopaneeli :button]))))
     (is (= 2 (count (u/sel [:#kartan-infopaneeli :.ip-otsikko]))))
     (is (= 0 (count (u/sel [:#kartan-infopaneeli :.kentan-label]))))
     (u/click (u/sel1 [:#kartan-infopaneeli :.ip-otsikko]))
     --
     "yhden asian 2 tietoa esillä klikkauksen jälkeen"
     (is (= 2 (count (u/sel [:#kartan-infopaneeli :.kentan-label]))))
     (println "nappeja" (count (u/sel [:#kartan-infopaneeli :.nappi-toissijainen])))
     (is (= 1 (count (u/sel [:#kartan-infopaneeli :.nappi-toissijainen])))))))
