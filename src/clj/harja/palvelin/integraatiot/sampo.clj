(ns harja.palvelin.integraatiot.sampo
  (:require [harja.kyselyt.maksuerat :as q]
            [harja.kyselyt.konversio :as konversio]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.sampo.maksuera :as maksuera]))

(defprotocol Maksueralahetys
  (laheta-maksuera-sampoon [this numero]))

(defn lukitse-maksuera [db numero]
  (let [lukko (str (java.util.UUID/randomUUID))]
    (log/debug "Lukitaan maksuera: " numero ", lukolla:" lukko)
    (let [onnistuiko? (= 1 (q/lukitse-maksuera! db lukko numero))]
      (log/debug "Onnistuiko: " onnistuiko?)
      onnistuiko?)))

(defn hae-maksuera [db numero]
  (konversio/alaviiva->rakenne (first (q/hae-lahetettava-maksuera db numero))))

(defn merkitse-maksuera-lahetetyksi [db numero lahetysid]
  (log/debug "Merkitään maksuerä: " numero " lähetetyksi ja avataan lukko ")
  (= 1 (q/merkitse-maksuera-lahetetyksi! db lahetysid numero)))


(defn muodosta-maksuera [db numero]
  (if (lukitse-maksuera db numero)
    (let [maksueran-tiedot (hae-maksuera db numero)
          maksuera-xml (maksuera/muodosta-maksuera-xml maksueran-tiedot)]
      maksuera-xml)
    nil))

(defn laheta-maksuera [sonja lahetysjono maksuera-xml]
  (sonja/laheta sonja lahetysjono "hephep"))

(defrecord Sampo [lahetysjono-ulos kuittausjono-ulos]
  com.stuartsierra.component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this)

  Maksueralahetys
  (laheta-maksuera-sampoon [this numero]
    (if-let [maksuera-xml (muodosta-maksuera (:db this) numero)]
      (if-let [lahetys-id (laheta-maksuera (:sonja this) lahetysjono-ulos maksuera-xml)]
        (merkitse-maksuera-lahetetyksi (:db this) numero lahetys-id)
        {:virhe :sonja-lahetys-epaonnistui})
      {:virhe :maksueran-lukitseminen-epaonnistui})))


