(ns harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.toimenpideinstanssit :as toimenpiteet]
            [harja.kyselyt.maksuerat :as maksuerat]
            [harja.kyselyt.kustannussuunnitelmat :as kustannussuunnitelmat]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+]]))

(def maksueratyypit ["kokonaishintainen" "yksikkohintainen" "lisatyo" "indeksi" "bonus" "sakko" "akillinen-hoitotyo" "muu"])

(defn perusta-maksuerat [db toimenpide-id toimenpidenimi]
  (log/debug "Perustetaan maksuerät toimenpideinstanssille id:" toimenpide-id " (" toimenpidenimi ")")
  (doseq [maksueratyyppi maksueratyypit]
    (let [maksueranumero (:numero (maksuerat/luo-maksuera<! db toimenpide-id maksueratyyppi (str toimenpidenimi " " maksueratyyppi)))]
      (kustannussuunnitelmat/luo-kustannussuunnitelma<! db maksueranumero))))

(defn paivita-toimenpide [db nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi toimenpide-id]
  (log/debug "Päivitetään toimenpide, jonka id on: " toimenpide-id ".")
  (toimenpiteet/paivita-toimenpideinstanssi! db nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi toimenpide-id))

(defn luo-toimenpide [db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi]
  (if (toimenpiteet/onko-tuotu-samposta? db sampo-toimenpidekoodi urakka-sampo-id)
    (log/warn "Toimenpide (koodi:" sampo-toimenpidekoodi ") on jo tuotu urakalle (Sampo id:" urakka-sampo-id "). Toimenpideinstanssia ei perusteta.")
    (do
      (log/debug "Luodaan uusi toimenpide.")
      (let [uusi-id (:id (toimenpiteet/luo-toimenpideinstanssi<! db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi))]
        (log/debug "Uusi toimenpide id on:" uusi-id)
        (perusta-maksuerat db uusi-id nimi)
        uusi-id))))

(defn tallenna-toimenpide [db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi]
  (let [toimenpide-id (:id (first (toimenpiteet/hae-id-sampoidlla db sampo-id)))]
    (if toimenpide-id
      (do
        (paivita-toimenpide db nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi toimenpide-id)
        toimenpide-id)
      (do
        (luo-toimenpide db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi)))))

(defn kasittele-toimenpide [db {:keys [viesti-id sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi]}]
  (log/debug "Käsitellään toimenpide Sampo id:llä: " sampo-id ", viesti id:" viesti-id)

  (if (empty? sampo-toimenpidekoodi)
    (do
      (log/warn "Samposta tuodulla toimenpideinstanssilla (id: " sampo-id ") ei ole toimenpidekoodia, joten sitä ei voi tallentaa")
      (kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Operation"))
    (try
      (let [toimenpide-id (tallenna-toimenpide db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi)]
        (log/debug "Käsiteltävän toimenpiteet id on:" toimenpide-id)
        (log/debug "Toimenpide käsitelty onnistuneesti")
        (kuittaus-sanoma/muodosta-onnistunut-kuittaus viesti-id "Operation"))
      (catch Exception e
        (log/error e "Tapahtui poikkeus tuotaessa toimenpidettä Samposta (Sampo id:" sampo-id ", viesti id:" viesti-id ").")
        (let [kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Operation" "Internal Error")]
          (throw+ {:type     virheet/+poikkeus-samposisaanluvussa+
                   :kuittaus kuittaus
                   :virheet  [{:poikkeus e}]}))))))

(defn kasittele-toimenpiteet [db toimenpiteet]
  (mapv #(kasittele-toimenpide db %) toimenpiteet))