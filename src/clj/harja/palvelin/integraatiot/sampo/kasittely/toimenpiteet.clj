(ns harja.palvelin.integraatiot.sampo.kasittely.toimenpiteet
  (:require [taoensso.timbre :as log]
            [harja.kyselyt.toimenpideinstanssit :as toimenpiteet]
            [harja.kyselyt.maksuerat :as maksuerat]
            [harja.kyselyt.kustannussuunnitelmat :as kustannussuunnitelmat]))


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
  (log/debug "Luodaan uusi toimenpide.")
  (let [uusi-id (:id (toimenpiteet/luo-toimenpideinstanssi<! db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi))]
    (log/debug "Uusi toimenpide id on:" uusi-id)
    (perusta-maksuerat db uusi-id nimi)
    uusi-id))

(defn tallenna-toimenpide [db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi]
  (let [toimenpide-id (:id (first (toimenpiteet/hae-id-sampoidlla db sampo-id)))]
    (if toimenpide-id
      (do
        (paivita-toimenpide db nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi toimenpide-id)
        toimenpide-id)
      (do
        (luo-toimenpide db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi)))))

(defn kasittele-toimenpide [db {:keys [viesti-id sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi]}]
  (log/debug "Tallennetaan uusi toimenpide sampo id:llä: " sampo-id)
  (let [toimenpide-id (tallenna-toimenpide db sampo-id nimi alkupvm loppupvm vastuuhenkilo-id talousosasto-id talousosasto-polku tuote-id tuote-polku urakka-sampo-id sampo-toimenpidekoodi)]
    (log/debug "Käsiteltävän toimenpiteet id on:" toimenpide-id)))

(defn kasittele-toimenpiteet [db toimenpiteet]
  (doseq [toimenpide toimenpiteet]
    (kasittele-toimenpide db toimenpide)))
