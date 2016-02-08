(ns harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [hiccup.core :refer [html]]
            [harja.tyokalut.xml :as xml]
            [clj-time.coerce :as coerce]
            [clj-time.core :as time]
            [harja.pvm :as pvm]
            [harja.kyselyt.kustannussuunnitelmat :as kustannussuunnitelmat]
            [harja.palvelin.integraatiot.sampo.sanomat.kustannussuunnitelma-sanoma :as kustannussuunitelma-sanoma]
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuera])
  (:import (java.util UUID)))

(def +xsd-polku+ "xsd/sampo/outbound/")

(defn tee-xml-sanoma [sisalto]
  (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" (html sisalto)))

(defn hae-kustannussuunnitelman-maksuera [db lahetys-id]
  (:maksuera (first (kustannussuunnitelmat/hae-maksuera-lahetys-idlla db lahetys-id))))

(defn lukitse-kustannussuunnitelma [db numero]
  (let [lukko (str (UUID/randomUUID))]
    (log/debug "Lukitaan kustannussuunnitelma:" numero ", lukolla:" lukko)
    (let [onnistuiko? (= 1 (kustannussuunnitelmat/lukitse-kustannussuunnitelma! db lukko numero))]
      onnistuiko?)))

(defn merkitse-kustannussuunnitelma-odottamaan-vastausta [db numero lahetys-id]
  (log/debug "Merkitään kustannussuuunnitelma: " numero " odottamaan vastausta ja avataan lukko. ")
  (= 1 (kustannussuunnitelmat/merkitse-kustannussuunnitelma-odottamaan-vastausta! db lahetys-id numero)))

(defn merkitse-kustannussuunnitelmalle-lahetysvirhe [db numero]
  (log/debug "Merkitään lähetysvirhe kustannussuunnitelmalle (numero:" numero ").")
  (= 1 (kustannussuunnitelmat/merkitse-kustannussuunnitelmalle-lahetysvirhe! db numero)))

(defn merkitse-kustannussuunnitelma-lahetetyksi [db numero]
  (log/debug "Merkitään kustannussuunnitelma (numero:" numero ") lähetetyksi.")
  (= 1 (kustannussuunnitelmat/merkitse-kustannussuunnitelma-lahetetyksi! db numero)))

(defn tee-oletus-vuosisummat [vuodet]
  (map #(hash-map :alkupvm (:alkupvm %), :loppupvm (:loppupvm %), :summa 1) vuodet))

(defn tee-vuosisummat [vuodet summat]
  (let [summat (into {} (map (juxt #(int (:vuosi %)) :summa)) summat)
        vuoden-ensimmainen-paiva #(pvm/vuoden-eka-pvm (pvm/vuosi %))
        vuoden-viimeinen-paiva #(pvm/vuoden-viim-pvm (pvm/vuosi %))
        formatteri (clj-time.format/with-zone (clj-time.format/formatter "yyyy-MM-dd hh:mm:ss") (clj-time.core/time-zone-for-id "EET"))
        formatoi-vuosi #(clj-time.format/unparse formatteri (clj-time.coerce/from-sql-date %))]
    (mapv (fn [vuosi]
            (let [summa (get summat (time/year (coerce/from-date (:loppupvm vuosi))) 0)]
              (println "alku: " (formatoi-vuosi (vuoden-ensimmainen-paiva(:alkupvm vuosi))))
              (println "loppu: " (formatoi-vuosi (vuoden-viimeinen-paiva(:loppupvm vuosi))))

              ;; todo: jatka tästä http://www.coderanch.com/t/547230/java/java/java-util-Date-getYear-method
              (def aika (java.util.Calendar/getInstance))
              (.setTime (java.util.Calendar/getInstance) (pvm/nyt))
              (.get aika java.util.Calendar/YEAR)

              {:alkupvm  (vuoden-ensimmainen-paiva (:alkupvm vuosi))
               :loppupvm (vuoden-viimeinen-paiva (:loppupvm vuosi))
               :summa    summa}))
          vuodet)))

(defn tee-vuosittaiset-summat [db numero maksueran-tiedot]
  (let [vuodet (mapv (fn [vuosi]
                       {:alkupvm  (first vuosi)
                        :loppupvm (second vuosi)})
                     (pvm/urakan-vuodet (:alkupvm (:toimenpideinstanssi maksueran-tiedot))
                                        (:loppupvm (:toimenpideinstanssi maksueran-tiedot))))]
    (case (:tyyppi (:maksuera maksueran-tiedot))
      "kokonaishintainen" (tee-vuosisummat vuodet (kustannussuunnitelmat/hae-kustannussuunnitelman-kokonaishintaiset-summat db numero))
      "yksikkohintainen" (tee-vuosisummat vuodet (kustannussuunnitelmat/hae-kustannussuunnitelman-yksikkohintaiset-summat db numero))
      (tee-oletus-vuosisummat vuodet))))

(defn muodosta-kustannussuunnitelma [db numero]
  (if (lukitse-kustannussuunnitelma db numero)
    (let [maksueran-tiedot (maksuera/hae-maksuera db numero)
          vuosittaiset-summat (tee-vuosittaiset-summat db numero maksueran-tiedot)
          maksueran-tiedot (assoc maksueran-tiedot :vuosittaiset-summat vuosittaiset-summat)
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

