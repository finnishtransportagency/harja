(ns harja.domain.lasku
  (:require [clojure.string :as str]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [clojure.spec.alpha :as s]
            [harja.domain.roolit :as roolit]
            [harja.pvm :as pvm]
    #?(:cljs [harja.tiedot.istunto :as istunto])))

(defonce kuukaudet-strs {:tammikuu  "Tammikuu"
                         :helmikuu  "Helmikuu"
                         :maaliskuu "Maaliskuu"
                         :huhtikuu  "Huhtikuu"
                         :toukokuu  "Toukokuu"
                         :kesakuu   "Kesäkuu"
                         :heinakuu  "Heinäkuu"
                         :elokuu    "Elokuu"
                         :syyskuu   "Syyskuu"
                         :lokakuu   "Lokakuu"
                         :marraskuu "Marraskuu"
                         :joulukuu  "Joulukuu"})

(defonce kuukaudet-keyword->number {:tammikuu  1
                                    :helmikuu  2
                                    :maaliskuu 3
                                    :huhtikuu  4
                                    :toukokuu  5
                                    :kesakuu   6
                                    :heinakuu  7
                                    :elokuu    8
                                    :syyskuu   9
                                    :lokakuu   10
                                    :marraskuu 11
                                    :joulukuu  12})

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
             (-> koontilaskun-kuukausi (str/split #"/") first keyword kuukaudet-keyword->number))
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

(s/def ::talenna-lasku (fn [lasku] true))
