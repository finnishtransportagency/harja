(ns harja.palvelin.integraatiot.sampo.kasittely.valitavoitteet
  "Valtakunnallisten välitavoitteiden asettaminen tuodulle Sampo-urakalle"
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.toimenpideinstanssit :as toimenpiteet]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodit]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuerat])
  (:use [slingshot.slingshot :only [throw+]]))

(defn kasittele-valitavoitteet [db]
  (log/debug "Käsitellään valtakunnalliset välitavoitteet")
  (try
    ;; TODO
    (catch Exception e
      (log/error e "Tapahtui poikkeus asetettaessa Sampo-urakalle valtakunnallisia välitavoitteita "
      (let [kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Operation" "Internal Error")]
        (throw+ {:type     virheet/+poikkeus-samposisaanluvussa+
                 :kuittaus kuittaus
                 :virheet  [{:poikkeus e}]}))))))