(ns harja.views.kartta.infopaneeli-test
  (:require  [cljs.test :as t :refer-macros [deftest is testing async]]
             [clojure.string :refer [join]]
             [reagent.core :as r]
             [harja.loki :refer [log tarkkaile! error]]
             [harja.views.kartta.infopaneeli :as sut]
             [harja.ui.kartta.infopaneelin-sisalto :as infopaneelin-sisalto]
             [cljs-time.core :as time]
             [harja.testutils.shared-testutils :as u])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(defn make-time [y m d h m s]
  (cljs-time.core/local-date-time y m d h m s))

(def testidata
  "mock @asiat-pisteessä -atomin sisällölle"
  {:koordinaatti [430704 7212576],
 :haetaan? false,
 :asiat
 [{:id 104,
   :alkanut (make-time 2016 12 01 12 00 00)
   :paattynyt (make-time 2016 12 01 12 00 00)
   :toimenpide "Auraus ja sohjonpoisto",
   :suorittaja {:nimi "ZYX Tekeminen Oy"},
   :tyyppi-kartalla :toteuma,
   :tierekisteriosoite {:numero 20 :alkuosa 5 :alkuetaisyys 0 :loppuosa 6 :loppuetaisyys 100}
   :tehtavat
   [{:toimenpide "Auraus ja sohjonpoisto",
     :maara 23,
     :yksikko "tiekm",
     :id 104}]}
  {:id 105,
   :alkanut (make-time 2016 12 02 11 00 00)
   :paattynyt (make-time 2016 12 02 11 00 00)
   :toimenpide "Auraus ja sohjonpoisto",
   :suorittaja {:nimi "ZYX Tekeminen Oy"},
   :tyyppi-kartalla :toteuma,
   :tierekisteriosoite {:numero 20 :alkuosa 1 :alkuetaisyys 0 :loppuosa 5 :loppuetaisyys 100}
   :tehtavat
   [{:toimenpide "Auraus ja sohjonpoisto",
     :maara 32,
     :yksikko "tiekm",
     :id 105}]}
  {:id 106,
   :alkanut (make-time 2016 12 02 11 00 00)
   :paattynyt (make-time 2016 12 02 11 00 00)
   :toimenpide "Suolaus",
   :suorittaja {:nimi "ZYX Tekeminen Oy"},
   :tyyppi-kartalla :toteuma,
   :tierekisteriosoite {:numero 20 :alkuosa 6 :alkuetaisyys 0 :loppuosa 7 :loppuetaisyys 100}
   :tehtavat
   [{:toimenpide "Suolaus", :maara 35, :yksikko "tiekm",
      :id 106}]}]})


(deftest edellytykset
  (is (pos? (count  (:asiat testidata))))
  (is (not (empty? (infopaneelin-sisalto/skeemamuodossa (:asiat testidata))))))

(defn pwa [x]
  (array (join " " x)))

(deftest otsikot
  (let [asiat-atomi (r/atom testidata)
        piilota-fn! #(log "piilota-fn! kutsuttu")
        linkkifunktiot (r/atom {:toteuma  {:teksti "linkkinappi" :toiminto #(fn [& argit] nil)}})]
    (tarkkaile! "mock-asiat-pisteessä" asiat-atomi)
    (komponenttitesti
     [:div.kartan-infopaneeli [sut/infopaneeli @asiat-atomi piilota-fn! linkkifunktiot]]
     (is (not (empty? (infopaneelin-sisalto/skeemamuodossa (:asiat @asiat-atomi)))))
     "infopaneelin sulkunappi ja otsikot, ei tietojen kenttiä"
     (is (= 1 (count (u/sel [:.kartan-infopaneeli :button]))))
     ;(is (= "Koordinaatit" (-> [:.kartan-infopaneeli :.ip-otsikko] u/sel first u/text)))
     (is (= 3 (count (u/sel [:.kartan-infopaneeli :.ip-otsikko]))))
     (is (= 0 (count (u/sel [:.kartan-infopaneeli :.kentan-label]))))
     (u/click (u/sel1 [:.kartan-infopaneeli :span.ip-haitari-otsikko]))
     --
     "yhden asian tiedot esillä klikkauksen jälkeen, ja linkkinappi myös"
     (is (= 5 (count (u/sel [:.kartan-infopaneeli :.tietorivi]))))
     (is (= 1 (count (u/sel [:.ip-osio :div :button])))))))
