(ns harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.toimenpideinstanssit :as toimenpiteet]
            [harja.kyselyt.maksuerat :as maksuerat]
            [harja.kyselyt.kustannussuunnitelmat :as kustannussuunnitelmat]
            [harja.kyselyt.toimenpideinstanssit :as toimenpideinstanssit]
            [harja.palvelin.integraatiot.sampo.sanomat.kuittaus-sampoon-sanoma :as kuittaus-sanoma]
            [harja.palvelin.integraatiot.sampo.tyokalut.virheet :as virheet]
            [harja.kyselyt.toimenpidekoodit :as toimenpidekoodit])
  (:use [slingshot.slingshot :only [throw+]]))

(def maksueratyypit ["kokonaishintainen" "yksikkohintainen" "lisatyo" "indeksi" "bonus" "sakko" "akillinen-hoitotyo" "muu"])

(defn tee-makseuran-nimi [db toimenpidekoodi maksueratyyppi]
  (let [emon-nimi (:nimi (first (toimenpidekoodit/hae-emon-nimi db toimenpidekoodi)))
        tyyppi (case maksueratyyppi
                 "kokonaishintainen" "Kokonaishintaiset"
                 "yksikkohintainen" "Yksikköhintaiset"
                 "lisatyo" "Lisätyöt"
                 "indeksi" "Indeksit"
                 "bonus" "Bonukset"
                 "sakko" "Sakot"
                 "akillinen-hoitotyo" "Äkilliset hoitotyöt"
                 "Muut")]
    (str emon-nimi ": " tyyppi)))

(defn perusta-maksuerat-toimenpiteelle [db toimenpide-id toimenpidekoodi]
  (log/debug "Perustetaan maksuerät toimenpideinstanssille id:" toimenpide-id)
  (doseq [maksueratyyppi maksueratyypit]
    (let [maksueran-nimi (tee-makseuran-nimi db toimenpidekoodi maksueratyyppi)
          maksueranumero (:numero (maksuerat/luo-maksuera<! db toimenpide-id maksueratyyppi maksueran-nimi))]
      (kustannussuunnitelmat/luo-kustannussuunnitelma<! db maksueranumero))))

(defn perusta-maksuerat-hoidon-urakoille [db]
  (log/debug "Perustetaan maksuerät hoidon maksuerättömille toimenpideinstansseille")
  (let [maksuerattomat-tpit (toimenpideinstanssit/hae-hoidon-maksuerattomat-toimenpideistanssit db)]
    (doseq [tpi maksuerattomat-tpit]
      (doseq [maksueratyyppi maksueratyypit]
        (let [maksueran-nimi (tee-makseuran-nimi db (:toimenpidekoodi tpi) maksueratyyppi)
              maksueranumero (:numero (maksuerat/luo-maksuera<! db (:toimenpide_id tpi) maksueratyyppi maksueran-nimi))]
          (kustannussuunnitelmat/luo-kustannussuunnitelma<! db maksueranumero))))))

(defn paivita-toimenpide [db nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi toimenpide-id]
  (log/debug "Päivitetään toimenpide, jonka id on: " toimenpide-id ".")
  (toimenpiteet/paivita-toimenpideinstanssi! db nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi toimenpide-id))

(defn luo-toimenpide [db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi]
  (log/debug "Luodaan uusi toimenpide.")
  (let [uusi-id (:id (toimenpiteet/luo-toimenpideinstanssi<! db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi))]
    (log/debug "Uusi toimenpide id on:" uusi-id)
    (perusta-maksuerat-toimenpiteelle db uusi-id sampo-toimenpidekoodi)
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
                 :virheet  [{:poikkeus e}]})))))

(defn kasittele-toimenpiteet [db toimenpiteet]
  (mapv #(kasittele-toimenpide db %) toimenpiteet))