(ns harja.palvelin.integraatiot.sampo.tuonti
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.sanomat.sampo-sanoma :as sampo-sanoma]
            [harja.palvelin.integraatiot.sampo.hankkeet :as hankkeet]
            [harja.palvelin.integraatiot.sampo.urakat :as urakat]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.testi :as testi])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-viesti [db kuittausjono-sisaan viesti]
  (log/debug "Vastaanotettiin Sonjan viestijonosta viesti: " viesti)
  (try+
    (jdbc/with-db-transaction [transaktio db]
      (let [data (sampo-sanoma/lue-viesti (.getText viesti))
            hankkeet (:hankkeet data)
            urakat (:urakat data)]
        (hankkeet/kasittele-hankkeet transaktio hankkeet)
        (urakat/kasittele-urakat transaktio urakat))
      ;; todo: laita komponentit palauttauttamaan suoraan ack/nack ja nakkaa ne yksi kerrallaan kuittausjonoon
      )

    ;; todo: laita päälle
    #_(catch Exception e)

    ))