(ns harja.palvelin.integraatiot.sampo.kasittely.maksuerat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [hiccup.core :refer [html]]
            [harja.kyselyt.maksuerat :as qm]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.sampo.sanomat.maksuera_sanoma :as maksuera-sanoma]
            [harja.kyselyt.toimenpideinstanssit :as toimenpideinstanssit]
            [harja.kyselyt.maksuerat :as maksuerat]
            [harja.kyselyt.kustannussuunnitelmat :as kustannussuunnitelmat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms])
  (:import (java.util UUID)))

(def maksueratyypit ["kokonaishintainen" "yksikkohintainen" "lisatyo" "indeksi" "bonus" "sakko" "akillinen-hoitotyo" "muu"])

(defn hae-maksuera [db numero]
  (let [{urakka-id :urakka-id :as maksuera} (konversio/alaviiva->rakenne (first (qm/hae-lahetettava-maksuera db numero)))
        tpi (get-in maksuera [:toimenpideinstanssi :id])
        tyyppi (keyword (get-in maksuera [:maksuera :tyyppi]))

        ;; Haetaan maksuerätiedot ja valitaan niistä tämän toimenpideinstanssin rivi
        summat (first (filter #(= (:tpi_id %) tpi)
                              (qm/hae-urakan-maksueratiedot db urakka-id)))]
    (assoc-in maksuera
              [:maksuera :summa]
              (get summat tyyppi))))

(defn hae-maksueranumero [db lahetys-id]
  (:numero (first (qm/hae-maksueranumero-lahetys-idlla db lahetys-id))))

(defn lukitse-maksuera [db numero]
  (let [lukko (str (UUID/randomUUID))]
    (log/debug "Lukitaan maksuera:" numero ", lukolla:" lukko)
    (let [onnistuiko? (= 1 (qm/lukitse-maksuera! db lukko numero))]
      onnistuiko?)))

(defn merkitse-maksuera-odottamaan-vastausta [db numero lahetys-id]
  (log/debug "Merkitään maksuerä: " numero " odottamaan vastausta ja avataan lukko. ")
  (= 1 (qm/merkitse-maksuera-odottamaan-vastausta! db lahetys-id numero)))

(defn merkitse-maksueralle-lahetysvirhe [db numero]
  (log/debug "Merkitään lähetysvirhe maksuerälle (numero:" numero ").")
  (= 1 (qm/merkitse-maksueralle-lahetysvirhe! db numero)))

(defn merkitse-maksuera-lahetetyksi [db numero]
  (log/debug "Merkitään maksuerä (numero:" numero ") lähetetyksi.")
  (= 1 (qm/merkitse-maksuera-lahetetyksi! db numero)))

(defn tee-makseuran-nimi [toimenpiteen-nimi maksueratyyppi]
  (let [tyyppi (case maksueratyyppi
                 "kokonaishintainen" "Kokonaishintaiset"
                 "yksikkohintainen" "Yksikköhintaiset"
                 "lisatyo" "Lisätyöt"
                 "indeksi" "Indeksit"
                 "bonus" "Bonukset"
                 "sakko" "Sakot"
                 "akillinen-hoitotyo" "Äkilliset hoitotyöt"
                 "Muut")]
    (str toimenpiteen-nimi ": " tyyppi)))

(defn perusta-maksuerat-hoidon-urakoille [db]
  (log/debug "Perustetaan maksuerät hoidon maksuerättömille toimenpideinstansseille")
  (let [maksuerattomat-tpit (toimenpideinstanssit/hae-hoidon-maksuerattomat-toimenpideistanssit db)]
    (if (empty? maksuerattomat-tpit)
      (log/debug "Kaikki maksuerät on jo perustettu hoidon urakoiden toimenpiteille"))
    (doseq [tpi maksuerattomat-tpit]
      (doseq [maksueratyyppi maksueratyypit]
        (let [maksueran-nimi (tee-makseuran-nimi (:toimenpide_nimi tpi) maksueratyyppi)
              maksueranumero (:numero (maksuerat/luo-maksuera<! db (:toimenpide_id tpi) maksueratyyppi maksueran-nimi))]
          (kustannussuunnitelmat/luo-kustannussuunnitelma<! db maksueranumero))))))

(defn hae-maksueran-tiedot [db numero]
  (let [maksueran-tiedot (hae-maksuera db numero)
        ;; Sakot lähetetään Sampoon negatiivisena
        maksueran-tiedot (if (= (:tyyppi (:maksuera maksueran-tiedot)) "sakko")
                           (update-in maksueran-tiedot [:maksuera :summa] -)
                           maksueran-tiedot)]
    maksueran-tiedot))

(defn tee-maksuera-jms-lahettaja [sonja integraatioloki db jono]
  (jms/jonolahettaja (integraatioloki/lokittaja integraatioloki db "sampo" "maksuera-lahetys") sonja jono))

(defn laheta-maksuera [sonja integraatioloki db lahetysjono-ulos numero]
  (log/debug "Lähetetään maksuera (numero: " numero ") Sampoon.")

  (if (lukitse-maksuera db numero)
    (let [viesti-id (str (UUID/randomUUID))
          jms-lahettaja (tee-maksuera-jms-lahettaja sonja integraatioloki db lahetysjono-ulos)
          maksuera (hae-maksueran-tiedot db numero)
          muodosta-xml #(maksuera-sanoma/muodosta maksuera)]
      (try
        (jms-lahettaja muodosta-xml viesti-id)
        (merkitse-maksuera-odottamaan-vastausta db numero viesti-id)
        (log/error (format "Maksuerä (numero: %s) merkittiin odottamaan vastausta." numero))

        (catch Exception e
          (log/error e (format "Maksuerän (numero: %s) lähetyksessä Sonjaan tapahtui poikkeus: %s." numero e))
          (merkitse-maksueralle-lahetysvirhe db numero))))

    (log/warn (format "Maksuerän (numero: %s) lukitus epäonnistui." numero))))

(defn kasittele-maksuera-kuittaus [db kuittaus viesti-id]
  (jdbc/with-db-transaction [db db]
    (if-let [maksueranumero (hae-maksueranumero db viesti-id)]
      (if (contains? kuittaus :virhe)
        (do
          (log/error "Vastaanotettiin virhe Sampon maksuerälähetyksestä: " kuittaus)
          (merkitse-maksueralle-lahetysvirhe db maksueranumero))
        (merkitse-maksuera-lahetetyksi db maksueranumero))
      (log/error "Viesti-id:llä " viesti-id " ei löydy maksuerää."))))
