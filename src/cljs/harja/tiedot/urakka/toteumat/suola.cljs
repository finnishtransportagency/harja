(ns harja.tiedot.urakka.toteumat.suola
  "Tämän nimiavaruuden avulla voidaan hakea urakan suola- ja lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.pvm :as pvm]
            [harja.atom :refer-macros [reaction<!]]
            [reagent.ratom :refer [reaction]]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(declare hae-toteumat hae-toteumat-tr-valille hae-materiaalit hae-toteumien-reitit! valittu-suolatoteuma? hae-toteuman-sijainti)

(defonce urakan-pohjavesialueet (atom nil))

(defonce suolatoteumissa? (atom false))

(defonce ui-suodatin-valinnat (atom {:suola "Kaikki"}))

(defonce suodatin-valinnat (atom {:suola "Kaikki"}))

(defonce kasinsyottolomake (atom {}))

(defonce materiaalit
  (reaction<! [hae? @suolatoteumissa?]
              (when hae?
                (hae-materiaalit))))

(defonce ui-valittu-aikavali (atom nil))

(defonce
  ^{:doc "Valittu aikaväli materiaalien tarkastelulle"}
  valittu-aikavali (atom nil))

(defonce ui-lomakkeen-tila (atom nil))
(defonce lomakkeen-tila (atom nil))

(defonce toteumat
  (reaction<! [hae? @suolatoteumissa?
               urakka @nav/valittu-urakka
               #_#_aikavali @valittu-aikavali ;; kommentoitu, ettei haku käynnistyisi automaattisesti
               tr-vali @lomakkeen-tila]
              {:nil-kun-haku-kaynnissa? true}
              (when (and hae? urakka @valittu-aikavali)
                (go
                  (let [tr-vali (:tierekisteriosoite tr-vali)]
                    (if (and (:numero tr-vali)
                             (:loppuosa tr-vali))
                      (<! (hae-toteumat-tr-valille (:id urakka) @valittu-aikavali
                                                   (:numero tr-vali)
                                                   (:alkuosa tr-vali)
                                                   (:alkuetaisyys tr-vali)
                                                   (:loppuosa tr-vali)
                                                   (:loppuetaisyys tr-vali)))
                      (<! (hae-toteumat (:id urakka) @valittu-aikavali))))))))

(def valitut-toteumat (atom #{}))

(defonce valitut-toteumat-kartalla
  (reaction<! [toteumat (distinct (map :tid @valitut-toteumat))
               valitun-urakan-id (:id @nav/valittu-urakka)]
              (when valitun-urakan-id
                (hae-toteumien-reitit! valitun-urakan-id toteumat))))

(defonce lampotilojen-hallinnassa? (atom false))

(def karttataso-suolatoteumat (atom false))

(defn hae-toteuman-sijainti [toteuma]
  
  (:sijainti toteuma))

(def suolatoteumat-kartalla
  (reaction
    (when @karttataso-suolatoteumat
      (kartalla-esitettavaan-muotoon
        (let [kaikki-toteumat (apply concat (map :toteumaidt @toteumat))
              yksittaiset-toteumat (filter
                                     #(valittu-suolatoteuma? %)
                                     (map (fn [tid]
                                            {:tid tid})
                                          kaikki-toteumat))]
          (map #(assoc % :tyyppi-kartalla :suolatoteuma
                       :sijainti (hae-toteuman-sijainti %))
               yksittaiset-toteumat))
        #(constantly false)))))

(defonce pohjavesialueen-toteuma (atom nil))

(defn eriteltavat-toteumat [toteumat]
  (map #(hash-map :tid (:tid %)) toteumat))

(defn valittu-suolatoteuma? [toteuma]
  (some #(= (:tid toteuma) (:tid %))
        @valitut-toteumat))

(defn valitse-suolatoteumat [toteumat]
  (reset! valitut-toteumat
          (into #{}
                (concat @valitut-toteumat
                        (eriteltavat-toteumat toteumat)))))

(defn hae-urakan-pohjavesialueet [urakka-id]
  {:pre [(int? urakka-id)]}
  (k/post! :hae-urakan-pohjavesialueet {:urakka-id urakka-id}))

(defn hae-pohjavesialueen-suolatoteuma [pohjavesialue [alkupvm loppupvm]]
  (k/post! :hae-pohjavesialueen-suolatoteuma {:pohjavesialue pohjavesialue
                                              :alkupvm alkupvm
                                              :loppupvm loppupvm}))

(defn poista-valituista-suolatoteumista [toteumat]
  (reset! valitut-toteumat
          (into #{}
                (remove (into #{}
                              (eriteltavat-toteumat toteumat))
                        @valitut-toteumat))))

(defn hae-toteumat-tr-valille [urakka-id [alkupvm loppupvm] tie alkuosa alkuet loppuosa loppuet]
  {:pre [(int? urakka-id)]}
  (k/post! :hae-suolatoteumat-tr-valille {:urakka-id urakka-id
                                          :alkupvm alkupvm
                                          :loppupvm loppupvm
                                          :tie tie
                                          :alkuosa alkuosa
                                          :alkuet alkuet
                                          :loppuosa loppuosa
                                          :loppuet loppuet}))

(defn hae-toteumat [urakka-id [alkupvm loppupvm]]
  {:pre [(int? urakka-id)]}
  (k/post! :hae-suolatoteumat {:urakka-id urakka-id
                               :alkupvm alkupvm
                               :loppupvm loppupvm}))

(defn hae-toteumien-reitit! [urakka-id toteuma-idt]
  {:pre [(int? urakka-id)]}
  (when (not (empty? toteuma-idt))
    (k/post! :hae-toteumien-reitit {:idt toteuma-idt :urakka-id urakka-id})))

(defn tallenna-toteumat [urakka-id sopimus-id rivit]
  {:pre [(int? urakka-id)]}
  (let [tallennettavat (into [] (->> rivit
                                     (filter (comp not :koskematon))
                                     (map #(assoc % :paattynyt (:alkanut %)))))]
    (k/post! :tallenna-suolatoteumat
             {:urakka-id urakka-id
              :sopimus-id sopimus-id
              :toteumat tallennettavat})))

(defn tallenna-kasinsyotetty-toteuma [urakka-id sopimus-id rivi]
  {:pre [(int? urakka-id)]}
  (k/post! :tallenna-kasinsyotetty-suolatoteuma
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :toteuma {:pvm (pvm/nyt)
                      :tierekisteriosoite (:tierekisteriosoite rivi)
                      :lisatieto (:lisatieto rivi)
                      :materiaali (:materiaali rivi)
                      :maara (:maara rivi)}}))

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
  {:pre [(int? urakka-id)]}
  (k/post! :hae-urakan-suolasakot-ja-lampotilat urakka-id))

(defn tallenna-teiden-hoitourakoiden-lampotilat [hoitokausi lampotilat]
  (let [lampotilat (mapv #(assoc % :alkupvm (first hoitokausi)
                                   :loppupvm (second hoitokausi))
                         (vec (vals lampotilat)))]
    (log "tallenna lämpötilat: " (pr-str lampotilat))
    (k/post! :tallenna-teiden-hoitourakoiden-lampotilat {:hoitokausi hoitokausi
                                                         :lampotilat lampotilat})))
