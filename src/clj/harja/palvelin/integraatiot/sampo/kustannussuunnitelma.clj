(ns harja.palvelin.integraatiot.sampo.kustannussuunnitelma
  (:require [harja.kyselyt.kustannussuunnitelmat :as qk]
            [harja.palvelin.integraatiot.sampo.sanomat.kustannussuunnitelma-sanoma :as kustannussuunitelma-sanoma]
            [harja.palvelin.integraatiot.sampo.maksuera :as maksuera]
            [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [hiccup.core :refer [html]]
            [clj-time.core :as t]
            [harja.tyokalut.xml :as xml])
  (:import (java.util UUID)))

(def +xsd-polku+ "test/xsd/sampo/outbound/")

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn hae-kustannussuunnitelman-maksuera [db lahetys-id]
  (:maksuera (first (qk/hae-maksuera-lahetys-idlla db lahetys-id))))

(defn lukitse-kustannussuunnitelma [db numero]
  (let [lukko (str (UUID/randomUUID))]
    (log/debug "Lukitaan kustannussuunnitelma:" numero ", lukolla:" lukko)
    (let [onnistuiko? (= 1 (qk/lukitse-kustannussuunnitelma! db lukko numero))]
      onnistuiko?)))

(defn merkitse-kustannussuunnitelma-odottamaan-vastausta [db numero lahetys-id]
  (log/debug "Merkitään kustannussuuunnitelma: " numero " odottamaan vastausta ja avataan lukko. ")
  (= 1 (qk/merkitse-kustannussuunnitelma-odottamaan-vastausta! db lahetys-id numero)))

(defn merkitse-kustannussuunnitelmalle-lahetysvirhe [db numero]
  (log/debug "Merkitään lähetysvirhe kustannussuunnitelmalle (numero:" numero ").")
  (= 1 (qk/merkitse-kustannussuunnitelmalle-lahetysvirhe! db numero)))

(defn merkitse-kustannussuunnitelma-lahetetyksi [db numero]
  (log/debug "Merkitään kustannussuunnitelma (numero:" numero ") lähetetyksi.")
  (= 1 (qk/merkitse-kustannussuunnitelma-lahetetyksi! db numero)))

(defn muodosta-kustannussuunnitelma [db numero]
  (if (lukitse-kustannussuunnitelma db numero)
    (let [maksueran-tiedot (maksuera/hae-maksuera db numero)
          kustannussuunnitelma-xml (tee-xml-sanoma (kustannussuunitelma-sanoma/muodosta maksueran-tiedot))]
      (if (xml/validoi +xsd-polku+ "nikuxog_costPlan.xsd" kustannussuunnitelma-xml)
        kustannussuunnitelma-xml
        (do
          (log/error "Kustannussuunnitelmaa ei voida lähettää. Kustannussuunnitelma XML ei ole validi.")
          nil)))
    nil))

(defn kasittele-kustannussuunnitelma-kuittaus [db kuittaus viesti-id]
  (jdbc/with-db-transaction [transaktio db]
    (if-let [maksuera (hae-kustannussuunnitelman-maksuera transaktio viesti-id)]
      (if (contains? kuittaus :virhe)
        (do
          (log/error "Vastaanotettiin virhe Sampon kustannussuunnitelmalähetyksestä: " kuittaus)
          (merkitse-kustannussuunnitelmalle-lahetysvirhe transaktio maksuera))
        (merkitse-kustannussuunnitelma-lahetetyksi transaktio maksuera))
      (log/error "Viesti-id:llä " viesti-id " ei löydy kustannussuunnitelmaa."))))


