(ns harja.palvelin.integraatiot.sampo
  (:require [harja.kyselyt.maksuerat :as qm]
            [harja.kyselyt.kustannussuunnitelmat :as qk]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.maksuera :as maksuera]
            [harja.palvelin.integraatiot.sampo.kustannussuunnitelma :as kustannussuunitelma]
            [harja.palvelin.integraatiot.sampo.kuittaus :as kuittaus]
            [clojure.java.jdbc :as jdbc]
            [hiccup.core :refer [html]]
            [clj-time.core :as t]
            [harja.tyokalut.xml :as xml])
  (:import (java.util UUID)))

(defprotocol Maksueralahetys
  (laheta-maksuera-sampoon [this numero]))

(def +xsd-polku+ "test/xsd/sampo/outbound/")

(defn hae-maksuera [db numero]
  (konversio/alaviiva->rakenne (first (qm/hae-lahetettava-maksuera db numero))))

(defn hae-maksueranumero [db lahetys-id]
  (:numero (first (qm/hae-maksueranumero-lahetys-idlla db lahetys-id))))

(defn hae-kustannussuunnitelman-maksuera [db lahetys-id]
  (:maksuera (first (qk/hae-maksuera-lahetys-idlla db lahetys-id))))

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn lukitse-maksuera [db numero]
  (let [lukko (str (UUID/randomUUID))]
    (log/debug "Lukitaan maksuera:" numero ", lukolla:" lukko)
    (let [onnistuiko? (= 1 (qm/lukitse-maksuera! db lukko numero))]
      onnistuiko?)))

(defn lukitse-kustannussuunnitelma [db numero]
  (let [lukko (str (UUID/randomUUID))]
    (log/debug "Lukitaan kustannussuunnitelma:" numero ", lukolla:" lukko)
    (let [onnistuiko? (= 1 (qk/lukitse-kustannussuunnitelma! db lukko numero))]
      onnistuiko?)))

(defn merkitse-maksuera-odottamaan-vastausta [db numero lahetys-id]
  (log/debug "Merkitään maksuerä: " numero " odottamaan vastausta ja avataan lukko. ")
  (= 1 (qm/merkitse-maksuera-odottamaan-vastausta! db lahetys-id numero)))

(defn merkitse-kustannussuunnitelma-odottamaan-vastausta [db numero lahetys-id]
  (log/debug "Merkitään kustannussuuunnitelma: " numero " odottamaan vastausta ja avataan lukko. ")
  (= 1 (qk/merkitse-kustannussuunnitelma-odottamaan-vastausta! db lahetys-id numero)))

(defn merkitse-maksueralle-lahetysvirhe [db numero]
  (log/debug "Merkitään lähetysvirhe maksuerälle (numero:" numero ").")
  (= 1 (qm/merkitse-maksueralle-lahetysvirhe! db numero)))

(defn merkitse-kustannussuunnitelmalle-lahetysvirhe [db numero]
  (log/debug "Merkitään lähetysvirhe kustannussuunnitelmalle (numero:" numero ").")
  (= 1 (qk/merkitse-kustannussuunnitelmalle-lahetysvirhe! db numero)))

(defn merkitse-maksuera-lahetetyksi [db numero]
  (log/debug "Merkitään maksuerä (numero:" numero ") lähetetyksi.")
  (= 1 (qm/merkitse-maksuera-lahetetyksi! db numero)))

(defn merkitse-kustannussuunnitelma-lahetetyksi [db numero]
  (log/debug "Merkitään kustannussuunnitelma (numero:" numero ") lähetetyksi.")
  (= 1 (qk/merkitse-kustannussuunnitelma-lahetetyksi! db numero)))

(defn muodosta-maksuera [db numero]
  (if (lukitse-maksuera db numero)
    (let [maksueran-tiedot (hae-maksuera db numero)
          maksuera-xml (tee-xml-sanoma (maksuera/muodosta-maksuera-sanoma maksueran-tiedot))]
      (if (xml/validoi +xsd-polku+ "nikuxog_product.xsd" maksuera-xml)
        maksuera-xml
        (do
          (log/error "Maksuerää ei voida lähettää. Maksuerä XML ei ole validi.")
          nil)))
    nil))

(defn muodosta-kustannussuunnitelma [db numero]
  (if (lukitse-kustannussuunnitelma db numero)
    (let [maksueran-tiedot (hae-maksuera db numero)
          kustannussuunnitelma-xml (tee-xml-sanoma (kustannussuunitelma/muodosta-kustannussuunnitelma-sanoma maksueran-tiedot))]
      (if (xml/validoi +xsd-polku+ "nikuxog_costPlan.xsd" kustannussuunnitelma-xml)
        kustannussuunnitelma-xml
        (do
          (log/error "Kustannussuunnitelmaa ei voida lähettää. Kustannussuunnitelma XML ei ole validi.")
          nil)))
    nil))

(defn laheta-sanoma-jonoon [sonja lahetysjono sanoma-xml]
  (sonja/laheta sonja lahetysjono sanoma-xml))

(defn kasittele-maksuera-kuittaus [db kuittaus viesti-id]
  (jdbc/with-db-transaction [transaktio db]
    (if-let [maksueranumero (hae-maksueranumero transaktio viesti-id)]
      (if (contains? kuittaus :virhe)
        (do
          (log/error "Vastaanotettiin virhe Sampon maksuerälähetyksestä: " kuittaus)
          (merkitse-maksueralle-lahetysvirhe transaktio maksueranumero))
        (merkitse-maksuera-lahetetyksi transaktio maksueranumero))
      (log/error "Viesti-id:llä " viesti-id " ei löydy maksuerää."))))

(defn kasittele-kustannussuunnitelma-kuittaus [db kuittaus viesti-id]
  (jdbc/with-db-transaction [transaktio db]
    (if-let [maksuera (hae-kustannussuunnitelman-maksuera transaktio viesti-id)]
      (if (contains? kuittaus :virhe)
        (do
          (log/error "Vastaanotettiin virhe Sampon kustannussuunnitelmalähetyksestä: " kuittaus)
          (merkitse-kustannussuunnitelmalle-lahetysvirhe transaktio maksuera))
        (merkitse-kustannussuunnitelma-lahetetyksi transaktio maksuera))
      (log/error "Viesti-id:llä " viesti-id " ei löydy kustannussuunnitelmaa."))))


(defn laheta-maksuera [sonja db lahetysjono-ulos numero]
  (log/debug "Lähetetään maksuera (numero: " numero ") Sampoon.")
  (if-let [maksuera-xml (muodosta-maksuera db numero)]
    (if-let [lahetys-id (laheta-sanoma-jonoon sonja lahetysjono-ulos maksuera-xml)]
      (merkitse-maksuera-odottamaan-vastausta db numero lahetys-id)
      (do
        (log/error "Maksuerän (numero: " numero ") lähetys Sonjaan epäonnistui.")
        (merkitse-maksueralle-lahetysvirhe db numero)
        {:virhe :sonja-lahetys-epaonnistui}))
    (do
      (log/warn "Maksuerän (numero: " numero ") lukitus epäonnistui.")
      {:virhe :maksueran-lukitseminen-epaonnistui})))

(defn laheta-kustannussuunitelma [sonja db lahetysjono-ulos numero]
  (log/debug "Lähetetään kustannussuunnitelma (numero: " numero ") Sampoon.")
  (if-let [kustannussuunnitelma-xml (muodosta-kustannussuunnitelma db numero)]
    (if-let [lahetys-id (laheta-sanoma-jonoon sonja lahetysjono-ulos kustannussuunnitelma-xml)]
      (merkitse-kustannussuunnitelma-odottamaan-vastausta db numero lahetys-id)
      (do
        (log/error "Kustannussuunnitelman (numero: " numero ") lähetys Sonjaan epäonnistui.")
        (merkitse-kustannussuunnitelmalle-lahetysvirhe db numero)
        {:virhe :sonja-lahetys-epaonnistui}))
    (do
      (log/warn "Kustannussuunnitelman (numero: " numero ") lukitus epäonnistui.")
      {:virhe :kustannussuunnitelman-lukitseminen-epaonnistui})))

(defn kasittele-kuittaus [db viesti]
  (log/debug "Vastaanotettiin Sonjan kuittausjonosta viesti: " viesti)
  (let [kuittaus (kuittaus/lue-kuittaus (.getText viesti))]
    (log/debug "Luettiin kuittaus: " kuittaus)
    (if-let [viesti-id (:viesti-id kuittaus)]
      (if (= :maksuera (:viesti-tyyppi kuittaus))
        (kasittele-maksuera-kuittaus db kuittaus viesti-id)
        (kasittele-kustannussuunnitelma-kuittaus db kuittaus viesti-id))
      (log/error "Sampon kuittauksesta ei voitu hakea viesti-id:tä."))))

(defn aja-paivittainen-lahetys [sonja db lahetysjono-ulos]
  (log/debug "Maksuerien päivittäinen lähetys käynnistetty: " (t/now))
  (let [maksuerat (qm/hae-likaiset-maksuerat db)
        kustannussuunnitelmat (qk/hae-likaiset-kustannussuunnitelmat db)]
    (log/debug "Lähetetään " (count maksuerat) " maksuerää ja " (count kustannussuunnitelmat) " kustannussuunnitelmaa.")
    (doseq [maksuera maksuerat]
      (laheta-maksuera sonja db lahetysjono-ulos (:numero maksuera)))
    (doseq [kustannussuunnitelma kustannussuunnitelmat]
      (laheta-kustannussuunitelma sonja db lahetysjono-ulos (:maksuera kustannussuunnitelma)))))

(defn tee-sonja-kuittauskuuntelija [this kuittausjono-ulos]
  (log/debug "Käynnistetään Sonja kuittauskuuntelija kuuntelemaan jonoa: " kuittausjono-ulos)
  (sonja/kuuntele (:sonja this) kuittausjono-ulos
                  (fn [viesti]
                    (kasittele-kuittaus (:db this) viesti))))

(defn tee-paivittainen-lahetys-tehtava [this paivittainen-lahetysaika lahetysjono-ulos]
  (if paivittainen-lahetysaika
    (do
      (log/debug "Ajastetaan maksuerien ja kustannussuunnitelmien lähetys ajettavaksi joka päivä kello: " paivittainen-lahetysaika)
      (ajastettu-tehtava/ajasta-paivittain
        paivittainen-lahetysaika
        (fn [_] (aja-paivittainen-lahetys (:sonja this) (:db this) lahetysjono-ulos))))
    (fn [] ())))

(defrecord Sampo [lahetysjono-ulos kuittausjono-ulos paivittainen-lahetysaika]
  com.stuartsierra.component/Lifecycle
  (start [this]
    (assoc this :sonja-kuittauskuuntelija (tee-sonja-kuittauskuuntelija this kuittausjono-ulos)
                :paivittainen-lahetys-tehtava (tee-paivittainen-lahetys-tehtava this paivittainen-lahetysaika lahetysjono-ulos)))
  (stop [this]
    (let [poista-kuuntelija (:sonja-kuittauskuuntelija this)
          poista-paivittainen-lahetys-tehtava (:paivittainen-lahetys-tehtava this)]
      (poista-kuuntelija)
      (poista-paivittainen-lahetys-tehtava))
    this)

  Maksueralahetys
  (laheta-maksuera-sampoon [this numero]
    (let [maksueran-lahetys (laheta-maksuera (:sonja this) (:db this) lahetysjono-ulos numero)
          kustannussuunnitelman-lahetys (laheta-kustannussuunitelma (:sonja this) (:db this) lahetysjono-ulos numero)]
      {:maksuera             maksueran-lahetys
       :kustannussuunnitelma kustannussuunnitelman-lahetys})))
