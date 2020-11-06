(ns harja.domain.lasku
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [harja.pvm :as pvm]))

(defonce hoitovuodet-strs {:1-hoitovuosi "1. hoitovuosi"
                           :2-hoitovuosi "2. hoitovuosi"
                           :3-hoitovuosi "3. hoitovuosi"
                           :4-hoitovuosi "4. hoitovuosi"
                           :5-hoitovuosi "5. hoitovuosi"})

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

(defn koontilaskun-kuukauden-sisalla?-fn [koontilaskun-kuukausi urakka-alkupvm urakka-loppupvm]
  (let [{kk :kk vuosi :vuosi} (koontilaskun-kuukausi->kk-vuosi koontilaskun-kuukausi urakka-alkupvm urakka-loppupvm)]
    #(if-not (nil? koontilaskun-kuukausi)
       (pvm/sama-kuukausi? % (pvm/->pvm (str "1." kk "." vuosi)))
       false))) ;pvm/jalkeen? % (pvm/nyt) --- otetaan käyttöön "joskus"

(s/def ::koontilaskun-kuukausi-formaatti (fn [txt]
                                           (let [[kuukausi hoitovuosi & loput] (str/split txt #"/")]
                                             (and (pvm/kuukauden-numero (str/lower-case kuukausi))
                                                  (contains? hoitovuodet-strs (keyword hoitovuosi))
                                                  (empty? loput)))))

(s/def ::koontilaskun-kuukausi ::koontilaskun-kuukausi-formaatti)

(s/def ::laskuerittely (s/keys :req-un [::koontilaskun-kuukausi]))

(s/def ::talenna-lasku (s/keys :req-un [::urakka-id ::laskuerittely]))
