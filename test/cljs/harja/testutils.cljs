(ns harja.testutils
  "Harjan omat testitykalut"
  (:require [cljs.test :as t :refer-macros [is]]
            [cljs-react-test.utils :as rt-utils]
            [dommy.core :as dommy]
            [reagent.core :as r]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >! timeout alts!] :as async]
            [harja.tiedot.istunto :as istunto]
            [cljs-react-test.simulate :as sim]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def kayttaja-jvh {:organisaation-urakat #{} :sahkoposti nil :kayttajanimi "jvh" :puhelin nil
                   :etunimi "Max" :sukunimi "Power"
                   :roolit #{"Jarjestelmavastaava"}
                   :organisaatioroolit {}
                   :id 2
                   :organisaatio {:id 1 :nimi "Liikennevirasto" :tyyppi "liikennevirasto"}
                   :urakkaroolit {}})

(def fake-palvelukutsut (atom nil))

(defn- suorita-fake-palvelukutsu [palvelu parametrit]
  (let [[kanava vastaus-fn] (get @fake-palvelukutsut palvelu)
        ch (async/chan)]
    (if kanava
      (go
        (let [vastaus (vastaus-fn parametrit)]
          (>! ch vastaus)
          (>! kanava vastaus))
        (async/close! kanava)
        (async/close! ch))
      (async/close! ch))
    ch))

(defn fake-palvelukutsu
  ([palvelu vastaus-fn] (fake-palvelukutsu palvelu vastaus-fn 30000))
  ([palvelu vastaus-fn timeout-ms]
   (let [ch (async/chan)
         timeout-ch (timeout timeout-ms)]
     (swap! fake-palvelukutsut assoc palvelu [ch vastaus-fn])
     (go (let [[val port] (alts! [ch timeout-ch])]
           (is (not= port timeout-ch) (str "Palvelukutsua " palvelu " ei tehty "
                                           timeout-ms " ajassa."))
           val)))))

(defn luo-kayttaja-fixture [kayttaja]
  (let [kayttaja-ennen (atom nil)]
    {:before #(do (reset! kayttaja-ennen @istunto/kayttaja)
                  (reset! istunto/kayttaja kayttaja))
     :after #(reset! istunto/kayttaja @kayttaja-ennen)}))

(def jvh-fixture (luo-kayttaja-fixture kayttaja-jvh))

(def fake-palvelut-fixture
  {:before #(do (reset! k/testmode suorita-fake-palvelukutsu)
                (reset! fake-palvelukutsut {}))
   :after #(do (reset! k/testmode nil)
               (reset! fake-palvelukutsut {}))})