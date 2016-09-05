(ns harja.palvelin.integraatiot.sampo.kasittely.urakat
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as urakat]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot]
            [harja.kyselyt.sopimukset :as sopimukset]
            [harja.kyselyt.hankkeet :as hankkeet]
            [harja.palvelin.integraatiot.sampo.kasittely.urakkatyyppi :as urakkatyyppi]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.kyselyt.toimenpideinstanssit :as toimenpiteet]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuerat]
            [harja.kyselyt.organisaatiot :as organisaatiot]
            [harja.tyokalut.merkkijono :as merkkijono]
            [harja.palvelin.integraatiot.sampo.kasittely.valitavoitteet :as valitavoitteet])
  (:use [slingshot.slingshot :only [throw+]]))

(defn- paivita-urakka [db nimi alkupvm loppupvm hanke-sampo-id urakka-id urakkatyyppi sopimustyyppi hallintayksikko]
  (log/debug "Päivitetään urakka, jonka id on: " urakka-id ".")
  (urakat/paivita-urakka! db nimi alkupvm loppupvm hanke-sampo-id urakkatyyppi hallintayksikko urakka-id sopimustyyppi))

(defn- luo-urakka [db nimi alkupvm loppupvm hanke-sampo-id sampo-id urakkatyyppi sopimustyyppi hallintayksikko]
  (log/debug "Luodaan uusi urakka.")
  (let [uusi-id (:id (urakat/luo-urakka<! db nimi alkupvm loppupvm hanke-sampo-id
                                          sampo-id urakkatyyppi hallintayksikko sopimustyyppi))]
    (log/debug "Uusi urakka id on:" uusi-id)
    (urakat/paivita-urakka-alueiden-nakyma db)
    uusi-id))

(defn- tallenna-urakka [db sampo-id nimi alkupvm loppupvm hanke-sampo-id urakkatyyppi sopimustyyppi ely-id]
  (let [urakka-id (:id (first (urakat/hae-id-sampoidlla db sampo-id)))]
    (if urakka-id
      (do
        (paivita-urakka db nimi alkupvm loppupvm hanke-sampo-id urakka-id urakkatyyppi sopimustyyppi ely-id)
        urakka-id)
      (do
        (luo-urakka db nimi alkupvm loppupvm hanke-sampo-id sampo-id urakkatyyppi sopimustyyppi ely-id)))))

(defn- paivita-yhteyshenkilo [db yhteyshenkilo-sampo-id urakka-id]
  (yhteyshenkilot/irrota-sampon-yhteyshenkilot-urakalta! db urakka-id)
  (yhteyshenkilot/liita-sampon-yhteyshenkilo-urakkaan<! db yhteyshenkilo-sampo-id urakka-id))

(defn- paivita-sopimukset [db urakka-sampo-id]
  (sopimukset/paivita-urakka-sampoidlla! db urakka-sampo-id))

(defn- paivita-toimenpiteet [db urakka-sampo-id]
  (toimenpiteet/paivita-urakka-sampoidlla! db urakka-sampo-id))

(defn- paattele-urakkatyyppi [db hanke-sampo-id]
  (let [sampo-tyypit (:sampo_tyypit (first (hankkeet/hae-sampo-tyypit db hanke-sampo-id)))
        urakkatyyppi (urakkatyyppi/paattele-urakkatyyppi sampo-tyypit)]
    (log/debug "Urakan tyyppi on:" urakkatyyppi)
    urakkatyyppi))

(defn- yllapito-urakka? [urakkatyyppi]
  ;; Samposta ei tuoda paikkausurakoita
  (some? (#{"paallystys" "tiemerkinta" "valaistus"} urakkatyyppi)))

(defn- luo-yllapidon-toimenpiteet [db {:keys [urakka-id urakkatyyppi alkupvm loppupvm] :as urakan-tiedot}]
  (when (yllapito-urakka? urakkatyyppi)
    (log/debug "Luodaan " urakkatyyppi "-urakalle toimenpideinstanssi")
    (let [yllapidon-3-tason-toimenpidekoodit {"paallystys" "PAAL_YKSHINT"
                                              "tiemerkinta" "TIEM_YKSHINT"
                                              "valaistus" "VALA_YKSHINT"}
          toimenpidekoodi (yllapidon-3-tason-toimenpidekoodit urakkatyyppi)]
      (when toimenpidekoodi
        (toimenpiteet/luo-yllapidon-toimenpideinstanssi<! db
                                                          toimenpidekoodi
                                                          alkupvm
                                                          loppupvm
                                                          urakka-id)))))

(defn paattele-sopimustyyppi [urakkatyyppi]
  (case urakkatyyppi
    "paallystys" "kokonaisurakka"
    "hoito" "palvelusopimus"
    nil))

(defn kasittele-urakka [db {:keys [viesti-id sampo-id nimi alkupvm loppupvm hanke-sampo-id
                                   yhteyshenkilo-sampo-id ely-hash]}]
  (log/debug "Käsitellään urakka Sampo id:llä: " sampo-id)
  (try
    (let [urakkatyyppi (paattele-urakkatyyppi db hanke-sampo-id)
          sopimustyyppi (paattele-sopimustyyppi urakkatyyppi)
          ely-id (:id (first (organisaatiot/hae-ely-id-sampo-hashilla db (merkkijono/leikkaa 5 ely-hash))))
          urakka-id (tallenna-urakka db sampo-id nimi alkupvm loppupvm hanke-sampo-id urakkatyyppi sopimustyyppi ely-id)]
      (log/debug "Käsiteltävän urakan id on:" urakka-id)
      (urakat/paivita-hankkeen-tiedot-urakalle! db hanke-sampo-id)
      (paivita-yhteyshenkilo db yhteyshenkilo-sampo-id urakka-id)
      (paivita-sopimukset db sampo-id)
      (paivita-toimenpiteet db sampo-id)
      (maksuerat/perusta-maksuerat-hoidon-urakoille db)
      (luo-yllapidon-toimenpiteet db {:urakka-id urakka-id
                                      :urakkatyyppi urakkatyyppi
                                      :alkupvm alkupvm
                                      :loppupvm loppupvm})
      (valitavoitteet/kasittele-urakan-valitavoitteet db sampo-id)

      (log/debug "Urakka käsitelty onnistuneesti")
      (kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Project"))
    (catch Exception e
      (log/error e "Tapahtui poikkeus tuotaessa urakkaa Samposta (Sampo id:" sampo-id ", viesti id:" viesti-id ").")
      (let [kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Project" "Internal Error")]
        (throw+ {:type virheet/+poikkeus-samposisaanluvussa+
                 :kuittaus kuittaus
                 :virheet [{:poikkeus e}]})))))

(defn kasittele-urakat [db urakat]
  (mapv #(kasittele-urakka db %) urakat))
