(ns harja.tiedot.urakka.toteumat
  "Tämä nimiavaruus hallinnoi urakan toteumien tietoja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [reagent.core :refer [atom]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.ui.protokollat :refer [Haku hae]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce yksikkohintaiset-tyot-nakymassa? (atom false))
(defonce erilliskustannukset-nakymassa? (atom false))

(def karttataso-yksikkohintainen-toteuma (atom false))

(def yksikkohintainen-toteuma-kartalla-xf
  (map #(do
         (assoc %
           :type :yksikkohintainen-toteuma
           :alue {:type   :arrow-line
                  :points (mapv (comp :coordinates :sijainti)
                                (sort-by
                                  :aika
                                  pvm/ennen?
                                  (:reittipisteet %)))}))))

(defonce valittu-yksikkohintainen-toteuma (atom nil))

(defonce yksikkohintainen-toteuma-kartalla
         (reaction
           @valittu-yksikkohintainen-toteuma
           (when @karttataso-yksikkohintainen-toteuma
             (into [] yksikkohintainen-toteuma-kartalla-xf [@valittu-yksikkohintainen-toteuma]))))

(defn hae-tehtavat [urakka-id]
  (k/post! :hae-urakan-tehtavat urakka-id))

(defn hae-materiaalit [urakka-id]
  (k/post! :hae-urakan-materiaalit urakka-id))

(defn hae-urakan-toteumat [urakka-id sopimus-id [alkupvm loppupvm] tyyppi]
  (k/post! :urakan-toteumat
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm
            :tyyppi tyyppi}))

(defn hae-urakan-toteuma [urakka-id toteuma-id]
  (k/post! :urakan-toteuma
           {:urakka-id urakka-id
            :toteuma-id toteuma-id}))

(defn hae-urakan-toteutuneet-tehtavat [urakka-id sopimus-id [alkupvm loppupvm] tyyppi]
  (k/post! :urakan-toteutuneet-tehtavat
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm
            :tyyppi tyyppi}))

(defn hae-urakan-toteumien-tehtavien-summat [urakka-id sopimus-id [alkupvm loppupvm] tyyppi]
  (k/post! :urakan-toteumien-tehtavien-summat
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm
            :tyyppi tyyppi}))

(defn hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla [urakka-id sopimus-id [alkupvm loppupvm] tyyppi toimenpidekoodi]
  (log "TOT Haetaan urakan toteutuneet tehtävät toimenpidekoodilla: " toimenpidekoodi)
  (k/post! :urakan-toteutuneet-tehtavat-toimenpidekoodilla
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm
            :tyyppi tyyppi
            :toimenpidekoodi toimenpidekoodi}))

(defn hae-urakan-toteuma-paivat [urakka-id sopimus-id [alkupvm loppupvm]]
  (k/post! :urakan-toteuma-paivat
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))

(defn tallenna-toteuma-ja-yksikkohintaiset-tehtavat [toteuma]
  (k/post! :tallenna-urakan-toteuma-ja-yksikkohintaiset-tehtavat toteuma))

(defn paivita-yk-hint-toteumien-tehtavat [urakka-id sopimus-id [alkupvm loppupvm] tyyppi tehtavat]
  (k/post! :paivita-yk-hint-toteumien-tehtavat {:urakka-id urakka-id
                                                :sopimus-id sopimus-id
                                                :alkupvm alkupvm
                                                :loppupvm loppupvm
                                                :tyyppi tyyppi
                                                :tehtavat tehtavat}))

(defn hae-urakan-erilliskustannukset [urakka-id [alkupvm loppupvm]]
  (k/post! :urakan-erilliskustannukset
           {:urakka-id urakka-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))

(defn tallenna-erilliskustannus [ek]
  (k/post! :tallenna-erilliskustannus ek))

(defn tallenna-toteuma-ja-toteumamateriaalit! [toteuma toteumamateriaalit hoitokausi sopimus-id]
  (k/post! :tallenna-toteuma-ja-toteumamateriaalit {:toteuma toteuma
                                                    :toteumamateriaalit toteumamateriaalit
                                                    :hoitokausi hoitokausi
                                                    :sopimus sopimus-id}))


(defn hae-urakan-muut-tyot [urakka-id sopimus-id [alkupvm loppupvm]]
  (log "tiedot: hae urakan muut työt" urakka-id sopimus-id alkupvm loppupvm)
  (k/post! :urakan-muut-tyot
           {:urakka-id urakka-id
            :sopimus-id sopimus-id
            :alkupvm alkupvm
            :loppupvm loppupvm}))
