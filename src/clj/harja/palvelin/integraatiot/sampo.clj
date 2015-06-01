(ns harja.palvelin.integraatiot.sampo
  (:require [harja.kyselyt.maksuerat :as qm]
            [harja.kyselyt.kustannussuunnitelmat :as qk]
            [harja.kyselyt.konversio :as konversio]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.maksuera :as maksuera]
            [harja.palvelin.integraatiot.sampo.kustannussuunnitelma :as kustannussuunitelma]
            [harja.palvelin.integraatiot.sampo.kuittaus :as kuittaus]
            [clojure.java.jdbc :as jdbc]))

(defprotocol Maksueralahetys
  (laheta-maksuera-sampoon [this numero]))

(defn hae-maksuera [db numero]
  (konversio/alaviiva->rakenne (first (qm/hae-lahetettava-maksuera db numero))))

(defn hae-maksueranumero [db lahetys-id]
  (:numero (first (qm/hae-maksueranumero-lahetys-idlla db lahetys-id))))

(defn hae-kustannussuunnitelman-maksuera [db lahetys-id]
  (:maksuera (first (qk/hae-maksuera-lahetys-idlla db lahetys-id))))

(defn lukitse-maksuera [db numero]
  (let [lukko (str (java.util.UUID/randomUUID))]
    (log/debug "Lukitaan maksuera:" numero ", lukolla:" lukko)
    (let [onnistuiko? (= 1 (qm/lukitse-maksuera! db lukko numero))]
      onnistuiko?)))

(defn lukitse-kustannussuunnitelma [db numero]
  (let [lukko (str (java.util.UUID/randomUUID))]
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
          maksuera-xml (maksuera/muodosta-maksuera-xml maksueran-tiedot)]
      maksuera-xml)
    nil))

(defn muodosta-kustannussuunnitelma [db numero]
  (if (lukitse-kustannussuunnitelma db numero)
    (let [maksueran-tiedot (hae-maksuera db numero)
          kustannussuunnitelma-xml (kustannussuunitelma/muodosta-kustannussuunnitelma-xml maksueran-tiedot)]
      kustannussuunnitelma-xml)
    nil))

(defn laheta-sanoma-jonoon [sonja lahetysjono sanoma-xml]
  (sonja/laheta sonja lahetysjono sanoma-xml))

(defn kasittele-maksuera-kuittaus [db kuittaus viesti-id]
  (jdbc/with-db-transaction [c db]
                            (if-let [maksueranumero (hae-maksueranumero c viesti-id)]
                              (if (contains? kuittaus :virhe)
                                (do
                                  (log/error "Vastaanotettiin virhe Sampon maksuerälähetyksestä: " kuittaus)
                                  (merkitse-maksueralle-lahetysvirhe c maksueranumero))
                                (merkitse-maksuera-lahetetyksi c maksueranumero))
                              (log/error "Viesti-id:llä " viesti-id " ei löydy maksuerää."))))

(defn kasittele-kustannussuunnitelma-kuittaus [db kuittaus viesti-id]
  (jdbc/with-db-transaction [c db]
                            (if-let [maksuera (hae-kustannussuunnitelman-maksuera c viesti-id)]
                              (if (contains? kuittaus :virhe)
                                (do
                                  (log/error "Vastaanotettiin virhe Sampon kustannussuunnitelmalähetyksestä: " kuittaus)
                                  (merkitse-kustannussuunnitelmalle-lahetysvirhe c maksuera))
                                (merkitse-kustannussuunnitelma-lahetetyksi c maksuera))
                              (log/error "Viesti-id:llä " viesti-id " ei löydy kustannussuunnitelmaa."))))


(defn laheta-maksuera [this lahetysjono-ulos numero]
  (log/debug "Lähetetään maksuera (numero: " numero ") Sampoon.")
  (if-let [maksuera-xml (muodosta-maksuera (:db this) numero)]
    (if-let [lahetys-id (laheta-sanoma-jonoon (:sonja this) lahetysjono-ulos maksuera-xml)]
      (merkitse-maksuera-odottamaan-vastausta (:db this) numero lahetys-id)
      (do
        (log/error "Maksuerän (numero: " numero ") lähetys Sonjaan epäonnistui.")
        (merkitse-maksueralle-lahetysvirhe (:db this) numero)
        {:virhe :sonja-lahetys-epaonnistui}))
    (do
      (log/warn "Maksuerän (numero: " numero ") lukitus epäonnistui.")
      {:virhe :maksueran-lukitseminen-epaonnistui})))

(defn laheta-kustannussuunitelma [this lahetysjono-ulos numero]
  (log/debug "Lähetetään kustannussuunnitelma (numero: " numero ") Sampoon.")
  (if-let [kustannussuunnitelma-xml (muodosta-kustannussuunnitelma (:db this) numero)]
    (if-let [lahetys-id (laheta-sanoma-jonoon (:sonja this) lahetysjono-ulos kustannussuunnitelma-xml)]
      (merkitse-kustannussuunnitelma-odottamaan-vastausta (:db this) numero lahetys-id)
      (do
        (log/error "Kustannussuunnitelman (numero: " numero ") lähetys Sonjaan epäonnistui.")
        (merkitse-kustannussuunnitelmalle-lahetysvirhe (:db this) numero)
        {:virhe :sonja-lahetys-epaonnistui}))
    (do
      (log/warn "Kustannussuunnitelman (numero: " numero ") lukitus epäonnistui.")
      {:virhe :kustannussuunnitelman-lukitseminen-epaonnistui}))
  )

(defn kasittele-kuittaus [db viesti]
  (log/debug "Vastaanotettiin Sonjan kuittausjonosta viesti: " viesti)
  (let [kuittaus (kuittaus/lue-kuittaus (.getText viesti))]
    (log/debug "Luettiin kuittaus: " kuittaus)
    (if-let [viesti-id (:viesti-id kuittaus)]
      (if (= :maksuera (:viesti-tyyppi kuittaus))
        (kasittele-maksuera-kuittaus db kuittaus viesti-id)
        (kasittele-kustannussuunnitelma-kuittaus db kuittaus viesti-id))
      (log/error "Sampon kuittauksesta ei voitu hakea viesti-id:tä."))))


(defrecord Sampo [lahetysjono-ulos kuittausjono-ulos]
  com.stuartsierra.component/Lifecycle
  (start [this]
    (assoc this :sonja-kuittauskuuntelija
                (sonja/kuuntele (:sonja this) kuittausjono-ulos
                                (fn [viesti]
                                  (kasittele-kuittaus (:db this) viesti)))))
  (stop [this]
    (let [poista-kuuntelija (:sonja-kuittauskuuntelija this)]
      (poista-kuuntelija))
    this)

  Maksueralahetys
  (laheta-maksuera-sampoon [this numero]
    {:maksuera             (laheta-maksuera this lahetysjono-ulos numero)
     :kustannussuunnitelma (laheta-kustannussuunitelma this lahetysjono-ulos numero)}))
