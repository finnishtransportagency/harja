(ns harja.palvelin.integraatiot.sampo.tuonti
  (:require [taoensso.timbre :as log]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.sanomat.viesti-sisaan-sanoma :as viesti-sisaan-sanoma]
            [harja.palvelin.integraatiot.sampo.hanke :as hanke]
            [clojure.java.jdbc :as jdbc])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn tallenna-hankkeet [db hankkeet]
  (log/debug "Hankkeet: " hankkeet)
  (doseq [hanke hankkeet]
    (log/debug "Hanke:" hanke)
    (hanke/luo-hanke db hanke)
    ;; todo: päivitä urakoille hanke ja alueurakkatiedot kohdalleen sampo-id:llä
    ))

(defn kasittele-viesti [db kuittausjono-sisaan viesti]
  (log/debug "Vastaanotettiin Sonjan viestijonosta viesti: " viesti)
  (try+
    (jdbc/with-db-transaction [transaktio db]
      (let [data (viesti-sisaan-sanoma/lue-viesti (.getText viesti))
            hankkeet (:hankkeet data)]
        (tallenna-hankkeet transaktio hankkeet))

      )
    #_(catch Exception e)

    ;;todo: käsittele poikkeus ja palauta nack
    ))