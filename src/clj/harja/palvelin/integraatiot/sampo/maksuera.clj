(ns harja.palvelin.integraatiot.sampo.maksuera
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [hiccup.core :refer [html]]
            [harja.tyokalut.xml :as xml]
            [harja.kyselyt.maksuerat :as qm]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.sampo.sanomat.maksuera_sanoma :as maksuera-sanoma])
  (:import (java.util UUID)))

(def +xsd-polku+ "resources/xsd/sampo/outbound/")

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn hae-maksuera [db numero]
  (konversio/alaviiva->rakenne (first (qm/hae-lahetettava-maksuera db numero))))

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

(defn muodosta-maksuera [db numero]
  (if (lukitse-maksuera db numero)
    (let [maksueran-tiedot (hae-maksuera db numero)
          maksuera-xml (tee-xml-sanoma (maksuera-sanoma/muodosta maksueran-tiedot))]
      (if (xml/validoi +xsd-polku+ "nikuxog_product.xsd" maksuera-xml)
        maksuera-xml
        (do
          (log/error "Maksuerää ei voida lähettää. Maksuerä XML ei ole validi.")
          nil)))
    nil))

(defn kasittele-maksuera-kuittaus [db kuittaus viesti-id]
  (jdbc/with-db-transaction [transaktio db]
    (if-let [maksueranumero (hae-maksueranumero transaktio viesti-id)]
      (if (contains? kuittaus :virhe)
        (do
          (log/error "Vastaanotettiin virhe Sampon maksuerälähetyksestä: " kuittaus)
          (merkitse-maksueralle-lahetysvirhe transaktio maksueranumero))
        (merkitse-maksuera-lahetetyksi transaktio maksueranumero))
      (log/error "Viesti-id:llä " viesti-id " ei löydy maksuerää."))))

