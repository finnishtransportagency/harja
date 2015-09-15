(ns harja.palvelin.integraatiot.sampo.kasittely.urakat
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.kyselyt.sopimukset :as sopimukset]
            [harja.kyselyt.toimenpideinstanssit :as toimenpiteet]
            [harja.kyselyt.hankkeet :as hankkeet]
            [harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi :as urakkatyyppi]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn paivita-urakka [db nimi alkupvm loppupvm hanke-sampo-id urakka-id urakkatyyppi]
  (log/debug "Päivitetään urakka, jonka id on: " urakka-id ".")
  (urakat/paivita-urakka! db nimi alkupvm loppupvm hanke-sampo-id urakkatyyppi urakka-id))

(defn luo-urakka [db nimi alkupvm loppupvm hanke-sampo-id sampo-id urakkatyyppi]
  (log/debug "Luodaan uusi urakka.")
  (let [uusi-id (:id (urakat/luo-urakka<! db nimi alkupvm loppupvm hanke-sampo-id sampo-id urakkatyyppi))]
    (log/debug "Uusi urakka id on:" uusi-id)
    (urakat/paivita-urakka-alaueiden-nakyma db)
    uusi-id))

(defn tallenna-urakka [db sampo-id nimi alkupvm loppupvm hanke-sampo-id urakkatyyppi]
  (let [urakka-id (:id (first (urakat/hae-id-sampoidlla db sampo-id)))]
    (if urakka-id
      (do
        (paivita-urakka db nimi alkupvm loppupvm hanke-sampo-id urakka-id urakkatyyppi)
        urakka-id)
      (do
        (luo-urakka db nimi alkupvm loppupvm hanke-sampo-id sampo-id urakkatyyppi)))))

(defn paivita-yhteyshenkilo [db yhteyshenkilo-sampo-id urakka-id]
  (yhteyshenkilot/irrota-sampon-yhteyshenkilot-urakalta! db urakka-id)
  (yhteyshenkilot/liita-sampon-yhteyshenkilo-urakkaan<! db yhteyshenkilo-sampo-id urakka-id))

(defn paivita-sopimukset [db urakka-sampo-id]
  (sopimukset/paivita-urakka-sampoidlla! db urakka-sampo-id))

(defn paivita-toimenpiteet [db urakka-sampo-id]
  (toimenpiteet/paivita-urakka-sampoidlla! db urakka-sampo-id))

(defn paattele-urakkatyyppi [db hanke-sampo-id]
  (let [alueurakkanumero (:alueurakkanro (first (hankkeet/hae-alueurakkanumero-sampoidlla db hanke-sampo-id)))
        urakkatyyppi (urakkatyyppi/paattele-urakkatyyppi alueurakkanumero)]
    (log/debug "Urakan tyyppi on:" urakkatyyppi)
    urakkatyyppi))

(defn kasittele-urakka [db {:keys [viesti-id sampo-id nimi alkupvm loppupvm hanke-sampo-id yhteyshenkilo-sampo-id]}]
  (log/debug "Käsitellään urakka Sampo id:llä: " sampo-id)
  (try
    (let [urakkatyyppi (paattele-urakkatyyppi db hanke-sampo-id)
          urakka-id (tallenna-urakka db sampo-id nimi alkupvm loppupvm hanke-sampo-id urakkatyyppi)]
      (log/debug "Käsiteltävän urakan id on:" urakka-id)
      (urakat/paivita-hankkeen-tiedot-urakalle! db hanke-sampo-id)
      (paivita-yhteyshenkilo db yhteyshenkilo-sampo-id urakka-id)
      (paivita-sopimukset db sampo-id)
      (paivita-toimenpiteet db sampo-id)

      (log/debug "Urakka käsitelty onnistuneesti")
      (kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Project"))
    (catch Exception e
      (log/error e "Tapahtui poikkeus tuotaessa urakkaa Samposta (Sampo id:" sampo-id ", viesti id:" viesti-id ").")
      (let [kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Project" "Internal Error")]
        (throw+ {:type     virheet/+poikkeus-samposisaanluvussa+
                 :kuittaus kuittaus
                 :virheet  [{:poikkeus e}]})))))

(defn kasittele-urakat [db urakat]
  (mapv #(kasittele-urakka db %) urakat))
