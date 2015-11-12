(ns harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.toimenpideinstanssit :as toimenpiteet]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuerat])
  (:use [slingshot.slingshot :only [throw+]]))

(defn paivita-toimenpide [db nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi toimenpide-id]
  (log/debug "Päivitetään toimenpide, jonka id on: " toimenpide-id ".")
  (toimenpiteet/paivita-toimenpideinstanssi! db nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi toimenpide-id))

(defn luo-toimenpide [db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi]
  (log/debug "Luodaan uusi toimenpide.")
  (let [uusi-id (:id (toimenpiteet/luo-toimenpideinstanssi<! db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi))]
    (log/debug "Uusi toimenpide id on:" uusi-id)
    uusi-id))

(defn tallenna-toimenpide [db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi]
  (let [toimenpide-id (:id (first (toimenpiteet/hae-id-sampoidlla db sampo-id)))]
    (if toimenpide-id
      (do
        (paivita-toimenpide db nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi toimenpide-id)
        toimenpide-id)
      (do
        (luo-toimenpide db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi)))))

(defn tarkista-toimenpide [db viesti-id sampo-urakka-id sampo-toimenpide-id sampo-toimenpidekoodi]
  (when (empty? sampo-toimenpidekoodi)
    (throw+ {:type     virheet/+poikkeus-samposisaanluvussa+
             :kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus viesti-id "Operation" "No operation code provided.")
             :virheet  [{:virhe "Toimenpiteelle ei ole annettu toimenpidekoodia (vv_operation)"}]}))
  (when (toimenpiteet/onko-tuotu-samposta? db sampo-toimenpidekoodi sampo-toimenpide-id sampo-urakka-id)
    (throw+ {:type     virheet/+poikkeus-samposisaanluvussa+
             :kuittaus (kuittaus-sanoma/muodosta-muu-virhekuittaus
                         viesti-id "Operation"
                         (str "Project: " sampo-urakka-id " already has operation: " sampo-toimenpidekoodi))
             :virheet  [{:virhe (str "Sampon projektille (id: " sampo-urakka-id ") on jo perustettu toimenpidekoodi: " sampo-toimenpidekoodi)}]})))

(defn kasittele-toimenpide [db {:keys [viesti-id sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi]}]
  (log/debug "Käsitellään toimenpide Sampo id:llä: " sampo-id ", viesti id:" viesti-id)

  (tarkista-toimenpide db viesti-id urakka-sampo-id sampo-id sampo-toimenpidekoodi)

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
                 :virheet  [{:poikkeus e}]}))))
  (maksuerat/perusta-maksuerat-hoidon-urakoille db))

(defn kasittele-toimenpiteet [db toimenpiteet]
  (mapv #(kasittele-toimenpide db %) toimenpiteet))