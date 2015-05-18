(ns harja.palvelin.integraatiot.sampo
  (:require [harja.kyselyt.maksuerat :as q]
            [harja.kyselyt.konversio :as konversio]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.maksuera :as maksuera]
            [harja.palvelin.integraatiot.sampo.kuittaus :as kuittaus]
            [clojure.java.jdbc :as jdbc]))

(defprotocol Maksueralahetys
  (laheta-maksuera-sampoon [this numero]))



(defn hae-maksuera [db numero]
  (konversio/alaviiva->rakenne (first (q/hae-lahetettava-maksuera db numero))))

(defn hae-maksuera-numero [db lahetys-id]
  (:numero (first (q/hae-maksueranumero-lahetys-idlla db lahetys-id))))

(defn lukitse-maksuera [db numero]
  (let [lukko (str (java.util.UUID/randomUUID))]
    (log/debug "Lukitaan maksuera:" numero ", lukolla:" lukko)
    (let [onnistuiko? (= 1 (q/lukitse-maksuera! db lukko numero))]
      onnistuiko?)))

(defn merkitse-maksuera-odottamaan-vastausta [db numero lahetysid]
  (log/debug "Merkitään maksuerä: " numero " odottamaan vastausta ja avataan lukko. ")
  (= 1 (q/merkitse-maksuera-odottamaan-vastausta! db lahetysid numero)))

(defn merkitse-maksueralle-lahetysvirhe [db numero]
  (log/debug "Merkitään lähetysvirhe maksuerälle:" numero ".")
  (= 1 (q/merkitse-maksueralle-lahetysvirhe! db numero)))

(defn merkitse-maksuera-lahetetyksi [db numero]
  (log/debug "Merkitään maksuerä:" numero " lähetetyksi.")
  (= 1 (q/merkitse-maksuera-lahetetyksi! db numero)))

(defn muodosta-maksuera [db numero]
  (if (lukitse-maksuera db numero)
    (let [maksueran-tiedot (hae-maksuera db numero)
          maksuera-xml (maksuera/muodosta-maksuera-xml maksueran-tiedot)]
      maksuera-xml)
    nil))


(defn laheta-maksuera [sonja lahetysjono maksuera-xml]
  (sonja/laheta sonja lahetysjono maksuera-xml))

(defn kasittele-kuittaus [db viesti]
  (jdb/with-db-transaction [c db]
     (log/debug "Vastaanotettiin Sonjan kuittausjonosta viesti: " viesti)
     (let [kuittaus (kuittaus/lue-kuittaus (.getText viesti))]
       (log/debug "Luettiin kuittaus: " kuittaus)

       (if-let [viesti-id (:viesti-id kuittaus)]
         (if-let [maksueranumero (hae-maksuera-numero c viesti-id)]
           (if (contains? kuittaus :virhe)
             (do
               (log/error "Vastaanotettiin virhe Sampon maksuerälähetyksestä: " kuittaus)
               (merkitse-maksueralle-lahetysvirhe c maksueranumero))
             (merkitse-maksuera-lahetetyksi c maksueranumero))
           (log/error "Viesti-id:llä " viesti-id " ei löydy maksuerää."))
         (log/error "Sampon kuittauksesta ei voitu hakea viesti-id:tä.")))))


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
    (if-let [maksuera-xml (muodosta-maksuera (:db this) numero)]
      (if-let [lahetys-id (laheta-maksuera (:sonja this) lahetysjono-ulos maksuera-xml)]
        (merkitse-maksuera-odottamaan-vastausta (:db this) numero lahetys-id)
        (do
          (log/error "Maksuerän (numero: " numero ") lähetys Sonjaan epäonnistui.")
          (merkitse-maksueralle-lahetysvirhe (:db this) numero)
          {:virhe :sonja-lahetys-epaonnistui}))
      (do
        (log/warn "Maksuerän (numero: " numero ") lukitus epäonnistui.")
        {:virhe :maksueran-lukitseminen-epaonnistui}))))


