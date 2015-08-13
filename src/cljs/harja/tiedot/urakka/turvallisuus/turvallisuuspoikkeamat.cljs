(ns harja.tiedot.urakka.turvallisuus.turvallisuuspoikkeamat
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.asiakas.tapahtumat :as tapahtumat])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))
(def +uusi-turvallisuuspoikkeama+ {})
(defonce valittu-turvallisuuspoikkeama (atom nil))

(defn hae-urakan-turvallisuuspoikkeamat
  [urakka-id [alku loppu]]
  (k/post! :hae-turvallisuuspoikkeamat {:urakka-id urakka-id
                                        :alku      alku
                                        :loppu     loppu}))

(defn hae-turvallisuuspoikkeama [urakka-id turvallisuuspoikkeama-id]
  (k/post! :hae-turvallisuuspoikkeama {:urakka-id urakka-id
                                       :turvallisuuspoikkeama-id turvallisuuspoikkeama-id}))

  
(defonce haetut-turvallisuuspoikkeamat (reaction<! [urakka-id (:id @nav/valittu-urakka)
                                                    hoitokausi @urakka/valittu-hoitokausi
                                                    nakymassa? @nakymassa?]
                                                   (when nakymassa?
                                                     (hae-urakan-turvallisuuspoikkeamat urakka-id hoitokausi))))

(def taso-turvallisuuspoikkeamat (atom false))

(def turvallisuuspoikkeama-kartalla-xf
  #(assoc %
    :type :turvallisuuspoikkeama
    :alue {:type        :circle
           :radius      (if (= (:id %) (:id @valittu-turvallisuuspoikkeama)) 10000 5000)
           :coordinates (:sijainti %)
           :fill        (if (= (:id %) (:id @valittu-turvallisuuspoikkeama)) {:color "green"} {:color "blue"}) ;;fixme väri ei toimi?
           :stroke      {:color "black" :width 10}}))

(defonce turvallisuuspoikkeamat-kartalla
         (reaction @valittu-turvallisuuspoikkeama
                   (when @taso-turvallisuuspoikkeamat
                     (into [] (map turvallisuuspoikkeama-kartalla-xf) @haetut-turvallisuuspoikkeamat))))

(defonce turvallisuuspoikkeamaa-klikattu
         (tapahtumat/kuuntele! :turvallisuuspoikkeama-klikattu
                               (fn [tp]
                                 (reset! valittu-turvallisuuspoikkeama (dissoc tp :type :alue)))))


(defn kasaa-tallennuksen-parametrit
  [tp]
  {:tp                 (assoc
                         (dissoc tp :liitteet :kommentit :korjaavattoimenpiteet :uusi-kommentti)
                         :urakka (:id @nav/valittu-urakka))
   :korjaavattoimenpiteet (:korjaavattoimenpiteet tp)
   ;; Lomakkeessa voidaan lisätä vain yksi kommentti kerrallaan, joka menee uusi-kommentti avaimeen
   ;; Täten tallennukseen ei tarvita :liitteitä eikä :kommentteja
   ;:liitteet           (:liitteet tp)
   ;:kommentit          (:kommentit tp)
   :uusi-kommentti     (:uusi-kommentti tp)
   :hoitokausi         @urakka/valittu-hoitokausi})

(defn tallenna-turvallisuuspoikkeama
  [tp]
  (k/post! :tallenna-turvallisuuspoikkeama (kasaa-tallennuksen-parametrit tp)))

(defn turvallisuuspoikkeaman-tallennus-onnistui
  [turvallisuuspoikkeamat]
  (reset! haetut-turvallisuuspoikkeamat turvallisuuspoikkeamat))

