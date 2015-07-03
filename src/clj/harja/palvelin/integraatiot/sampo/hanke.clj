(ns harja.palvelin.integraatiot.sampo.hanke
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.hankkeet :as hankkeet]
            [harja.kyselyt.urakat :as urakat]))

(defn luo-hanke [db {:keys [nimi alkupvm loppupvm alueurakkanro sampo-id]}]
  (log/debug "Tallennetaan uusi hanke sampo id:llä: " sampo-id)
  (hankkeet/luo-hanke<! db nimi alkupvm loppupvm alueurakkanro sampo-id)
  (urakat/paivita-hankkeen-tiedot-urakalle! db sampo-id))