(ns harja.palvelin.integraatiot.sampo.kasittely.hankkeet
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.hankkeet :as hankkeet]
            [harja.kyselyt.urakat :as urakat]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuerat]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn kasittele-hanke [db {:keys [viesti-id nimi alkupvm loppupvm sampo-id]}]
  (log/debug "Käsitellään hanke Sampo id:llä: " sampo-id)
  (try
    (let []
      (if (hankkeet/onko-tuotu-samposta? db sampo-id)
        (hankkeet/paivita-hanke-samposta! db nimi alkupvm loppupvm sampo-id)
        (hankkeet/luo-hanke<! db nimi alkupvm loppupvm sampo-id))

      (urakat/paivita-hankkeen-tiedot-urakalle! db sampo-id)
      (maksuerat/perusta-maksuerat-hoidon-urakoille db))

    (log/debug "Hanke käsitelty onnistuneesti")
    (kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Program")

    (catch Exception e
      (log/error e "Tapahtui poikkeus tuotaessa hanketta Samposta (Sampo id:" sampo-id ", viesti id:" viesti-id ").")
      (let [kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Program" "Internal Error")]
        (throw+ {:type     virheet/+poikkeus-samposisaanluvussa+
                 :kuittaus kuittaus
                 :virheet  [{:poikkeus e}]})))))

(defn kasittele-hankkeet [db hankkeet]
  (mapv #(kasittele-hanke db %) hankkeet))
