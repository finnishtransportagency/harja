(ns harja.palvelin.integraatiot.sampo.kasittely.hankkeet
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.hankkeet :as hankkeet]
            [harja.kyselyt.urakat :as urakat]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuerat]
            [harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi :as urakkatyyppi]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn pura-alueurakkanro [alueurakkanro]
  (let [osat (clojure.string/split alueurakkanro #"-")]
    (if (= 2 (count osat))
      {:tyypit (first osat) :alueurakkanro (second osat)}
      {:tyypit nil :alueurakkanro alueurakkanro})))

(defn kasittele-hanke [db {:keys [viesti-id nimi alkupvm loppupvm alueurakkanro sampo-id]}]
  (log/debug "Käsitellään hanke Sampo id:llä: " sampo-id)
  (try
    (let [tyyppi-ja-alueurakkanro (pura-alueurakkanro alueurakkanro)
          tyypit (:tyypit tyyppi-ja-alueurakkanro)
          alueurakkanro (:alueurakkanro tyyppi-ja-alueurakkanro)
          urakkatyyppi (urakkatyyppi/paattele-urakkatyyppi tyypit)]
      (if (hankkeet/onko-tuotu-samposta? db sampo-id)
        (hankkeet/paivita-hanke-samposta! db nimi alkupvm loppupvm alueurakkanro tyypit sampo-id)
        (hankkeet/luo-hanke<! db nimi alkupvm loppupvm alueurakkanro tyypit sampo-id))

      (urakat/paivita-hankkeen-tiedot-urakalle! db sampo-id)
      (urakat/paivita-tyyppi-hankkeen-urakoille! db urakkatyyppi sampo-id))

    (log/debug "Hanke käsitelty onnistuneesti")
    (kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Program")

    (catch Exception e
      (log/error e "Tapahtui poikkeus tuotaessa hanketta Samposta (Sampo id:" sampo-id ", viesti id:" viesti-id ").")
      (let [kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Program" "Internal Error")]
        (throw+ {:type     virheet/+poikkeus-samposisaanluvussa+
                 :kuittaus kuittaus
                 :virheet  [{:poikkeus e}]}))))
  (maksuerat/perusta-maksuerat-hoidon-urakoille db))

(defn kasittele-hankkeet [db hankkeet]
  (mapv #(kasittele-hanke db %) hankkeet))