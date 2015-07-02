(ns harja.palvelin.integraatiot.sampo.tuonti
  (:require [taoensso.timbre :as log]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.sanomat.viesti-sisaan-sanoma :as viesti-sisaan-sanoma])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-viesti [db kuittausjono-sisaan viesti]
  (log/debug "Vastaanotettiin Sonjan viestijonosta viesti: " viesti)
  (try+
    (let [data (viesti-sisaan-sanoma/lue-viesti (.getText viesti))])
    (catch Exception e)
    ;;todo: käsittele poikkeus ja palauta nack
    ))