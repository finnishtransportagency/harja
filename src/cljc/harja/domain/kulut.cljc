(ns harja.domain.kulut
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [harja.pvm :as pvm]))

(defonce hoitovuodet-strs {:1-hoitovuosi "1. hoitovuosi"
                           :2-hoitovuosi "2. hoitovuosi"
                           :3-hoitovuosi "3. hoitovuosi"
                           :4-hoitovuosi "4. hoitovuosi"
                           :5-hoitovuosi "5. hoitovuosi"
                           :6-hoitovuosi "6. hoitovuosi"
                           :7-hoitovuosi "7. hoitovuosi"})

(defn koontilaskun-kuukausi->kk-vuosi [koontilaskun-kuukausi urakka-alkupvm urakka-loppupvm]
  (let [hoitovuosi->vuosiluku (into {} (map-indexed (fn [indeksi vuosi]
                                                      [(keyword (str (inc indeksi) "-hoitovuosi")) vuosi])
                                                    (range (pvm/vuosi urakka-alkupvm)
                                                           (pvm/vuosi urakka-loppupvm))))
        kk (when-not (nil? koontilaskun-kuukausi)
             (-> koontilaskun-kuukausi (str/split #"/") first pvm/kuukauden-numero))
        vuosi (when-not (nil? koontilaskun-kuukausi)
                (-> koontilaskun-kuukausi
                    (str/split #"/")
                    second
                    keyword
                    hoitovuosi->vuosiluku))
        vuosi (when-not (nil? vuosi)
                (if (< kk 10)
                  (inc vuosi)
                  vuosi))]
    {:kk kk :vuosi vuosi}))

(defn pvm->koontilaskun-kuukausi
  [paivamaara urakka-alkupvm]
  (let [alkuvuosi (pvm/vuosi urakka-alkupvm)
        koontilaskun-vuosi (cond-> paivamaara 
                             true pvm/vuosi
                             true (- alkuvuosi)
                             (> (pvm/kuukausi paivamaara) 9) inc)
        koontilaskun-kuukausi (pvm/kuukauden-nimi (pvm/kuukausi paivamaara))]
    (str koontilaskun-kuukausi "/" koontilaskun-vuosi "-hoitovuosi")))

(defn koontilaskun-kuukausi->pvm [koontilaskun-kuukausi urakka-alkupvm urakka-loppupvm]
  (let [{kk :kk vuosi :vuosi} (koontilaskun-kuukausi->kk-vuosi koontilaskun-kuukausi urakka-alkupvm urakka-loppupvm)] 
    (pvm/->pvm-date-timeksi (str "1." kk "." vuosi))))

(defn koontilaskun-kuukauden-sisalla?-fn [koontilaskun-kuukausi urakka-alkupvm urakka-loppupvm]
  (let [{kk :kk vuosi :vuosi} (koontilaskun-kuukausi->kk-vuosi koontilaskun-kuukausi urakka-alkupvm urakka-loppupvm)]
    #(if-not (nil? koontilaskun-kuukausi)
       (pvm/sama-kuukausi? % (pvm/->pvm-date-timeksi (str "1." kk "." vuosi)))
       false))) ;pvm/jalkeen? % (pvm/nyt) --- otetaan käyttöön "joskus"

(s/def ::koontilaskun-kuukausi-formaatti (fn [txt]
                                           (let [[kuukausi hoitovuosi & loput] (str/split txt #"/")]
                                             (and (pvm/kuukauden-numero (str/lower-case kuukausi))
                                                  (contains? hoitovuodet-strs (keyword hoitovuosi))
                                                  (empty? loput)))))

(s/def ::koontilaskun-kuukausi ::koontilaskun-kuukausi-formaatti)

(s/def ::kulu-kohdistuksineen (s/keys :req-un [::koontilaskun-kuukausi]))

(s/def ::talenna-kulu (s/keys :req-un [::urakka-id ::kulu-kohdistuksineen]))
