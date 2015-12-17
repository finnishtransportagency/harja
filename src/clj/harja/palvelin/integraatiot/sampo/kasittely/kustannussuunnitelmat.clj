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
            [harja.palvelin.integraatiot.sampo.kasittely.maksuerat :as maksuera]
            [clj-time.periodic :as time-period])
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


(defn luo-vuosisumma [vuosi summa]
  {:alkupvm  (pvm/aika-iso8601 (coerce/to-date (:alkupvm vuosi)))
   :loppupvm (pvm/aika-iso8601 (coerce/to-date (:loppupvm vuosi)))
   :summa    summa})

(defn tee-kokonaishintaiset-vuosisummat [db numero vuodet]
  (let [summat (kustannussuunnitelmat/hae-kustannussuunnitelman-kokonaishintaiset-summat db numero)
        summat-vuosittain (group-by :vuosi summat)]
    (mapv (fn [vuosi]
            (let [summa (reduce + (map :summa (get summat-vuosittain (time/year (:alkupvm vuosi)))))]
              (luo-vuosisumma vuosi summa))) vuodet)))

(defn tee-yksikköhintaiset-vuosisummat [db numero vuodet]
  (let [summat (kustannussuunnitelmat/hae-kustannussuunnitelman-yksikkohintaiset-summat db numero)
        summat-vuosittain (group-by #(time/year
                                      (time/to-time-zone
                                        (coerce/from-sql-date (:alkupvm %))
                                        (time/time-zone-for-offset 2))) summat)]
    (mapv (fn [vuosi]
            (let [summa (reduce + (map :summa (get summat-vuosittain (time/year (:alkupvm vuosi)))))]
              (luo-vuosisumma vuosi summa))) vuodet)))

(defn tee-oletus-vuosisummat [vuodet]
  (map #(hash-map :alkupvm (:alkupvm %), :loppupvm (:loppupvm %), :summa 1) vuodet))


(defn aikavali
  [alku loppu askel]
  (let [vali (time-period/periodic-seq alku askel)
        valilla? (fn [aika] (time/within? (time/interval alku loppu) aika))]
    (take-while valilla? vali)))

(defn muodosta-vuosi [alkupvm loppupvm]
  {:alkupvm  alkupvm
   :loppupvm loppupvm})

(defn muodosta-valivuosien-elementit [alkuvuosi taysien-vuosien-maara]
  (vec
    (for [i (range taysien-vuosien-maara)]
      (do
        (inc i)
        (let [vuosi (+ 1 i alkuvuosi)]
          (muodosta-vuosi (time/first-day-of-the-month vuosi 1) (time/last-day-of-the-month vuosi 12)))))))

(defn luo-vuodet [alkupvm loppupvm]
  (let [alkuvuosi (time/year alkupvm)
        loppuvuosi (time/year loppupvm)
        kuukausien-maara (count (aikavali alkupvm loppupvm (time/months 1)))
        ensimmaisen-vuoden-kuukaudet (- 13 (time/month alkupvm))
        viimeisen-vuoden-kuukaudet (rem (- kuukausien-maara ensimmaisen-vuoden-kuukaudet) 12)
        taysien-vuosien-maara (/ (- kuukausien-maara ensimmaisen-vuoden-kuukaudet viimeisen-vuoden-kuukaudet) 12)
        ensimmainen-vuosi [(muodosta-vuosi alkupvm (time/last-day-of-the-month (time/year alkupvm) 12))]
        valivuodet (when (< 0 taysien-vuosien-maara)
                     (muodosta-valivuosien-elementit alkuvuosi taysien-vuosien-maara))
        viimeinen-vuosi (when (< alkuvuosi loppuvuosi)
                          [(muodosta-vuosi (time/first-day-of-the-month loppuvuosi 1) loppupvm)])]
    (vec (concat ensimmainen-vuosi valivuodet viimeinen-vuosi))))

(defn tee-vuosittaiset-summat [db numero maksueran-tiedot]
  (let [alkupvm (coerce/from-sql-date (:alkupvm (:toimenpideinstanssi maksueran-tiedot)))
        loppupvm (coerce/from-sql-date (:loppupvm (:toimenpideinstanssi maksueran-tiedot)))
        vuodet (luo-vuodet alkupvm loppupvm)]
    (case (:tyyppi (:maksuera maksueran-tiedot))
      "kokonaishintainen" (tee-kokonaishintaiset-vuosisummat db numero vuodet)
      "yksikkohintainen" (tee-yksikköhintaiset-vuosisummat db numero vuodet)
      (tee-oletus-vuosisummat vuodet))))

(defn muodosta-kustannussuunnitelma [db numero]
  (if (lukitse-kustannussuunnitelma db numero)
    (let [maksueran-tiedot (maksuera/hae-maksuera db numero)
          vuosittaiset-summat (tee-vuosittaiset-summat db numero maksueran-tiedot)
          maksueran-tiedot (:assoc maksueran-tiedot :vuosittaiset-summat vuosittaiset-summat)
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


