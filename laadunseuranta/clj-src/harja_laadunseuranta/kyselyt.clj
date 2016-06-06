(ns harja-laadunseuranta.kyselyt
  (:require [yesql.core :refer [defqueries]]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [taoensso.timbre :as log]))

(defqueries "harja_laadunseuranta/kyselyt/kyselyt.sql")

(def db tietokanta/db)

(def jatkuvat-vakiohavainto-idt (delay (into #{} (map :id (hae-jatkuvat-vakiohavainto-idt nil {:connection @db})))))

(defn jatkuvat-havainnot [havainnot]
  (filterv @jatkuvat-vakiohavainto-idt havainnot))

(defn pistemainen-havainto [havainnot]
  (first (filterv (comp not @jatkuvat-vakiohavainto-idt) havainnot)))

(defn muunna-merkinta [merkinta]
  (let [p (when (:sijainti merkinta)
            (.getGeometry (:sijainti merkinta)))
        havainnot (when (:havainnot merkinta)
                    (seq (.getArray (:havainnot merkinta))))]
    (-> merkinta
     (assoc :sijainti [(.x p) (.y p)]
            :jatkuvat-havainnot (jatkuvat-havainnot havainnot)
            :pistemainen-havainto (pistemainen-havainto havainnot))
     (dissoc :havainnot))))

(defn hae-reitin-merkinnat [args db]
  (mapv muunna-merkinta (hae-reitin-merkinnat-raw args db)))

(defn hae-vakiohavaintojen-kuvaukset [db]
  (into {} (mapv (fn [r] [(keyword (:avain r)) (:nimi r)])
                 (hae-pistemaiset-vakiohavainnot {} {:connection db}))))

(defn hae-vakiohavaintoavaimet [db]
  (into {} (mapv (fn [r] [(keyword (:avain r)) (:id r)])
                 (hae-vakiohavaintojen-avaimet {} {:connection db}))))

(defn hae-vakiohavaintoidt [db]
  (into {} (mapv (fn [r] [(:id r) (keyword (:avain r))])
                 (hae-vakiohavaintojen-avaimet {} {:connection db}))))


(def vakiohavainto-idt (delay (try
                                (hae-vakiohavaintoidt @db)
                                (catch Exception e
                                  {7 :sulamisvesihaittoja
                                   20 :siltasaumoissa-puutteita
                                   27 :reunataytto-puutteellinen
                                   1 :liukasta
                                   24 :ylijaamamassa-tasattu-huonosti
                                   39 :viheralueet-hoitamatta
                                   4 :liikennemerkki-luminen
                                   15 :sohjoa
                                   21 :siltavaurioita
                                   31 :nakemaalue-raivaamatta
                                   32 :niittamatta
                                   40 :rumpu-tukossa
                                   33 :vesakko-raivaamatta
                                   13 :pl-alue-auraamatta
                                   22 :silta-puhdistamatta
                                   36 :reunapaalut-likaisia
                                   41 :rumpu-liettynyt
                                   43 :kaidevaurio
                                   29 :istutukset-hoitamatta
                                   44 :kiveysvaurio
                                   6 :aurausvalli
                                   28 :reunapalletta
                                   25 :oja-tukossa
                                   34 :liikennemerkki-vinossa
                                   17 :lumikielekkeita
                                   3 :lumista
                                   12 :pl-epatasainen-polanne
                                   2 :tasauspuute
                                   23 :ojat-kivia-poistamatta
                                   35 :reunapaalut-vinossa
                                   19 :soratie
                                   11 :pysakki-hiekoittamatta
                                   9 :hiekoittamatta
                                   5 :pysakilla-epatasainen-polanne
                                   14 :pl-alue-hiekoittamatta
                                   26 :luiskavaurio
                                   16 :irtolunta
                                   38 :pl-alue-korjattavaa
                                   30 :liikennetila-hoitamatta
                                   10 :pysakki-auraamatta
                                   18 :yleishavainto
                                   42 :rumpu-rikki
                                   37 :pl-alue-puhdistettava
                                   8 :polanteessa-jyrkat-urat}))))
