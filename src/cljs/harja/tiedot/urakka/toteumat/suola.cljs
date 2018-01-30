(ns harja.tiedot.urakka.toteumat.suola
  "Tämän nimiavaruuden avulla voidaan hakea urakan suola- ja lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.atom :refer-macros [reaction<!]]
            [reagent.ratom :refer [reaction]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(declare hae-toteumat hae-materiaalit hae-toteumien-reitit!)

(defonce suolatoteumissa? (atom false))

(defonce suodatin-valinnat (atom {:suola "Kaikki"}))

(defonce materiaalit
  (reaction<! [hae? @suolatoteumissa?]
              (when hae?
                (hae-materiaalit))))

(defonce
  ^{:doc "Valittu aikaväli materiaalien tarkastelulle"}
  valittu-aikavali (atom nil))

(defonce toteumat
  (reaction<! [hae? @suolatoteumissa?
               urakka @nav/valittu-urakka
               aikavali @valittu-aikavali]
              {:nil-kun-haku-kaynnissa? true}
              (when (and hae? urakka aikavali)
                (go
                  (into []
                        ;; luodaan kaikille id
                        (map-indexed (fn [i rivi] (assoc rivi :id i)))

                        (<! (hae-toteumat (:id urakka) aikavali)))))))

(defonce valitut-toteumat (atom #{}))

(defonce valitut-toteumat-kartalla
  (reaction<! [toteumat @valitut-toteumat]
              (hae-toteumien-reitit! @nav/valittu-urakka toteumat)))

(defonce lampotilojen-hallinnassa? (atom false))

(defonce valittu-suolatoteuma (atom nil))

(def karttataso-suolatoteumat (atom false))

(defn valittu-suolatoteuma? [suolatoteuma]
  (and @valittu-suolatoteuma suolatoteuma (= (:tid suolatoteuma) (:tid @valittu-suolatoteuma))))

(defn hae-toteuman-sijainti [toteuma]
  (:sijainti (first (filter #(= (:tid toteuma) (:id %)) @valitut-toteumat-kartalla))))

(defonce suolatoteumat-kartalla
  (reaction
    (when @karttataso-suolatoteumat
      (kartalla-esitettavaan-muotoon
        (let [yksittaiset-toteumat (filter
                                     #(contains? @valitut-toteumat (:tid %))
                                     (apply concat (map :toteumat @toteumat)))]
          (map #(assoc % :tyyppi-kartalla :suolatoteuma
                         :sijainti (hae-toteuman-sijainti %)) yksittaiset-toteumat))
        #(valittu-suolatoteuma? %)))))

(defn hae-toteumat [urakka-id [alkupvm loppupvm]]
  (k/post! :hae-suolatoteumat {:urakka-id urakka-id
                               :alkupvm alkupvm
                               :loppupvm loppupvm}))

(defn hae-toteumien-reitit! [urakka-id toteuma-idt]
  (when (not (empty? toteuma-idt))
    (k/post! :hae-toteumien-reitit {:idt toteuma-idt :urakka-id urakka-id})))

(defn tallenna-toteumat [urakka-id sopimus-id rivit]
  (let [tallennettavat (into [] (->> rivit
                                     (filter (comp not :koskematon))
                                     (map #(assoc % :paattynyt (:alkanut %)))))]
    (k/post! :tallenna-suolatoteumat
             {:urakka-id urakka-id
              :sopimus-id sopimus-id
              :toteumat tallennettavat})))

(defn hae-materiaalit []
  (k/get! :hae-suolamateriaalit))

(defn hae-lampotilat-ilmatieteenlaitokselta [talvikauden-alkuvuosi]
  (k/post! :hae-lampotilat-ilmatieteenlaitokselta {:vuosi talvikauden-alkuvuosi} nil true))

(defn hae-teiden-hoitourakoiden-lampotilat [hoitokausi]
  (k/post! :hae-teiden-hoitourakoiden-lampotilat {:hoitokausi hoitokausi}))

(def hoitokaudet
  (vec
    (let [nyt (pvm/nyt)
          tama-vuosi (pvm/vuosi nyt)
          ;; sydäntalvi on joulu-helmikuu, tarjotaan sydäntalven keskilämpöltilan hakua aikaisintaan
          ;; maaliskuussa. Ei tarpeen huomioida karkauspäivää koska manuaalinen integraatio.
          sydantalvi-ohi? (pvm/jalkeen? nyt (pvm/->pvm (str "28.2." tama-vuosi)))
          vanhin-haettava-vuosi 2005]
      (for [vuosi (range vanhin-haettava-vuosi
                         (if sydantalvi-ohi?
                           tama-vuosi
                           (dec tama-vuosi)))]
        [(pvm/hoitokauden-alkupvm vuosi) (pvm/hoitokauden-loppupvm (inc vuosi))]))))

(defonce valittu-hoitokausi (atom (last hoitokaudet)))

(defn valitse-hoitokausi! [tk]
  (reset! valittu-hoitokausi tk))

(defonce hoitourakoiden-lampotilat
  (reaction<! [lampotilojen-hallinnassa? @lampotilojen-hallinnassa?
               valittu-hoitokausi @valittu-hoitokausi]
              {:nil-kun-haku-kaynnissa? true}
              (when (and lampotilojen-hallinnassa?
                         valittu-hoitokausi)
                (hae-teiden-hoitourakoiden-lampotilat valittu-hoitokausi))))

(defn hae-urakan-suolasakot-ja-lampotilat [urakka-id]
  (k/post! :hae-urakan-suolasakot-ja-lampotilat urakka-id))

(defn aseta-suolasakon-kaytto [urakka-id kaytossa?]
  (k/post! :aseta-suolasakon-kaytto {:urakka-id urakka-id
                                     :kaytossa? kaytossa?}))

(defn tallenna-teiden-hoitourakoiden-lampotilat [hoitokausi lampotilat]
  (let [lampotilat (mapv #(assoc % :alkupvm (first hoitokausi)
                                   :loppupvm (second hoitokausi))
                         (vec (vals lampotilat)))]
    (log "tallenna lämpötilat: " (pr-str lampotilat))
    (k/post! :tallenna-teiden-hoitourakoiden-lampotilat {:hoitokausi hoitokausi
                                                         :lampotilat lampotilat})))
