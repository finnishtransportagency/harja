(ns harja.testutils
  "Harjan omat testityökalut"
  (:require [cljs.test :as t :refer-macros [is]]
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
    (if vastaus-fn
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

(defn- clear-timeouts []
  (let [max-timeout-id (.setTimeout js/window :D 0)]
    (dotimes [i max-timeout-id]
      (.clearTimeout js/window i))))

(def fake-palvelut-fixture
  {:before #(do (reset! k/testmode suorita-fake-palvelukutsu)
                (reset! fake-palvelukutsut {}))
   :after #(do (clear-timeouts)
               (reset! k/testmode nil)
               (reset! fake-palvelukutsut {}))})

(defn =marginaalissa?
  "Palauttaa ovatko kaksi lukua samat virhemarginaalin sisällä. Voi käyttää esim. doublelaskennan
  tulosten vertailussa. Oletusmarginaali on 0.05"
  ([eka toka] (=marginaalissa? eka toka 0.05))
  ([eka toka marginaali]
   (< (Math/abs (double (- eka toka))) marginaali)))

(defn tarkista-map-arvot
  "Tarkistaa, että mäpissä on oikeat arvot. Numeroita vertaillaan =marginaalissa? avulla, muita
  = avulla. Tarkistaa myös, että kaikki arvot ovat olemassa. Odotetussa mäpissa saa olla
  ylimääräisiä avaimia."
  [odotetut saadut]
  (doseq [k (keys odotetut)
          :let [odotettu-arvo (get odotetut k)
                saatu-arvo (get saadut k ::ei-olemassa)]]
    (cond
      (= saatu-arvo ::ei-olemassa)
      (is false (str "Odotetussa mäpissä ei arvoa avaimelle: " k
                     ", odotettiin arvoa: " odotettu-arvo))

      (and (number? odotettu-arvo) (number? saatu-arvo))
      (is (=marginaalissa? odotettu-arvo saatu-arvo)
          (str "Saatu arvo numeroavaimelle " k " ei marginaalissa, odotettu: "
               odotettu-arvo ", saatu: "
               saatu-arvo))

      ;; (instance? java.util.Date odotettu-arvo)
      ;; (is (=ts odotettu-arvo saatu-arvo)
      ;;     (str "Odotettu date arvo pvm-avaimelle " k " ei ole millisekunteina sama, odotettu: "
      ;;          odotettu-arvo " (" (type odotettu-arvo) "), saatu: "
      ;;          saatu-arvo " (" (type saatu-arvo) ")"))

      :default
      (is (= odotettu-arvo saatu-arvo)
          (str "Saatu arvo avaimelle " k " ei täsmää, odotettu: " odotettu-arvo
               ", saatu: " saatu-arvo)))))
