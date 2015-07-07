(ns harja.palvelin.integraatiot.sampo.kasittely.hankkeet
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.hankkeet :as hankkeet]
            [harja.kyselyt.urakat :as urakat]))

(defn kasittele-hanke [db {:keys [nimi alkupvm loppupvm alueurakkanro sampo-id]}]
  (log/debug "Käsitellään hanke Sampo id:llä: " sampo-id)
  (if (hankkeet/onko-tuotu-samposta? db sampo-id)
    (hankkeet/paivita-hanke-samposta! db nimi alkupvm loppupvm alueurakkanro sampo-id)
    (hankkeet/luo-hanke<! db nimi alkupvm loppupvm alueurakkanro sampo-id))
  (urakat/paivita-hankkeen-tiedot-urakalle! db sampo-id))

(defn kasittele-hankkeet [db hankkeet]
  (doseq [hanke hankkeet]
    (kasittele-hanke db hanke)))