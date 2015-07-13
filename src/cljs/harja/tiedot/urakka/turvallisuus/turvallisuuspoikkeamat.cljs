(ns harja.tiedot.urakka.turvallisuus.turvallisuuspoikkeamat
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))
(def +uusi-turvallisuuspoikkeama+ {})

(defonce valittu-turvallisuuspoikkeama (atom nil))

(defn hae-urakan-turvallisuuspoikkeamat
  [urakka-id [alku loppu]]
  (k/post! :hae-turvallisuuspoikkeamat {:urakka-id urakka-id
                                        :alku      alku
                                        :loppu     loppu}))

(defonce haetut-turvallisuuspoikkeamat (reaction<! [urakka-id (:id @nav/valittu-urakka)
                                                    hoitokausi @urakka/valittu-hoitokausi
                                                    nakymassa?]
                                                   (when nakymassa?
                                                     (hae-urakan-turvallisuuspoikkeamat urakka-id hoitokausi))))

(defn kasaa-tallennuksen-parametrit
  [tp]
  {})

(defn tallenna-turvallisuuspoikkeama
  [tp]
  (k/post! :tallenna-turvallisuuspoikkeama (kasaa-tallennuksen-parametrit tp)))

(defn turvallisuuspoikkeaman-tallennus-onnistui
  [palautettu-id turvallisuuspoikkeama]
  (if (some #(= (:id %) palautettu-id) @haetut-turvallisuuspoikkeamat)
    (reset! haetut-turvallisuuspoikkeamat
            (into [] (map (fn [vanha] (if (= palautettu-id (:id vanha)) turvallisuuspoikkeama vanha)) @haetut-turvallisuuspoikkeamat)))

    (reset! haetut-turvallisuuspoikkeamat
            (into [] (concat @haetut-turvallisuuspoikkeamat turvallisuuspoikkeama)))))