(ns harja.palvelin.ajastetut-tehtavat.suolasakkojen-lahetys
  (:require [chime :refer [chime-ch]]
            [com.stuartsierra.component :as component]
            [clj-time.core :as t]
            [clojure.core.async :as a :refer [<! go-loop]]
            [clj-time.periodic :refer [periodic-seq]]
            [taoensso.timbre :as log]
            [harja.kyselyt.maksuerat :as maksuerat]
            [chime :refer [chime-at]])
  (:import (org.joda.time DateTimeZone)))

(defn tee-sekvenssi-vuoden-ajankohdalle [kuukausi paiva tunti]
  (periodic-seq (..
                  (t/date-time (t/year (t/now)) kuukausi paiva tunti)
                  (withZone (DateTimeZone/forID "Europe/Helsinki")))
                (t/years 1)))

(def tee-aikataulu
  "Suolasakkojen lähetys tehdään touko-, kesä-, heinä-, syys- ja elokuun ensimmäisenä päivänä klo 02:00"
  (concat
    (tee-sekvenssi-vuoden-ajankohdalle 5 1 2)
    (tee-sekvenssi-vuoden-ajankohdalle 6 1 2)
    (tee-sekvenssi-vuoden-ajankohdalle 7 1 2)
    (tee-sekvenssi-vuoden-ajankohdalle 8 1 2)
    (tee-sekvenssi-vuoden-ajankohdalle 9 1 2)))

(defn merkitse-sakkomaksuerat-likaisiksi [db]
  (maksuerat/merkitse-tyypin-maksuerat-likaisiksi! db "sakko"))

(defn tee-suolasakkojen-lahetys-tehtava [this]
  (log/debug "Ajastetaan suolasakkojen merkitseminen likaiseksi.")
  (chime-at (tee-aikataulu)
            (fn [_]
              (log/debug "Merkitään suolasakot likaisiksi seuraavaa Sampo-lähetystä varten")
              (merkitse-sakkomaksuerat-likaisiksi (:db this)))))

(defrecord SuolasakkojenLahetys []
  component/Lifecycle
  (start [this]
    (assoc this :suolasakkojen-lahetys-tehtava (tee-suolasakkojen-lahetys-tehtava this))
    this)
  (stop [this]
    (let [poista-suolasakkojen-lahetys (:suolasakkojen-lahetys-tehtava this)]
      (poista-suolasakkojen-lahetys))
    this))