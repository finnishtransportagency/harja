(ns harja.palvelin.integraatiot.sampo.kasittely.valitavoitteet
  "Valtakunnallisten välitavoitteiden asettaminen tuodulle Sampo-urakalle"
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.toimenpideinstanssit :as toimenpiteet]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodit]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuerat])
  (:use [slingshot.slingshot :only [throw+]]))

(defn lisaa-urakan-puuttuvat-valtakunnalliset-valitavoitteet [db sampo-id]

  )

(defn kasittele-urakka [db {:keys [sampo-id]}]
  (log/debug "Käsitellään sampo-id:n " sampo-id " urakan valtakunnalliset välitavoitteet")
  (lisaa-urakan-puuttuvat-valtakunnalliset-valitavoitteet db sampo-id))

(defn kasittele-valitavoitteet [db urakat]
  (mapv #(kasittele-urakka db %) urakat))