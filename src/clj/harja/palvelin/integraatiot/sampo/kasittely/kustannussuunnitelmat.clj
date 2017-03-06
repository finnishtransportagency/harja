(ns harja.palvelin.integraatiot.sampo.kasittely.kustannussuunnitelmat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [hiccup.core :refer [html]]
            [clj-time.coerce :as coerce]
            [clj-time.core :as time]
            [harja.pvm :as pvm]
            [harja.kyselyt.kustannussuunnitelmat :as kustannussuunnitelmat]
            [harja.kyselyt.maksuerat :as maksuerat]
            [harja.palvelin.integraatiot.sampo.sanomat.kustannussuunnitelma-sanoma :as kustannussuunitelma-sanoma]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.konversio :as konversio])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (java.util UUID Calendar TimeZone)))

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

(defn aseta-pvm [pvm vuosi kuukausi paiva]
  (.setTime pvm vuosi)
  (.set pvm Calendar/MONTH kuukausi)
  (.set pvm Calendar/DAY_OF_MONTH paiva)
  (.setTimeZone pvm (TimeZone/getTimeZone "EET")))

(defn rakenna-vuosi [vuosi summa]
  (let [alku (Calendar/getInstance)
        loppu (Calendar/getInstance)]
    (aseta-pvm alku (:alkupvm vuosi) Calendar/JANUARY 1)
    (aseta-pvm loppu (:loppupvm vuosi) Calendar/DECEMBER 31)
    {:alkupvm (pvm/aika-iso8601 (.getTime alku))
     :loppupvm (pvm/aika-iso8601 (.getTime loppu))
     :summa summa}))

(defn tee-oletus-vuosisummat [vuodet]
  (map (fn [vuosi] (rakenna-vuosi vuosi 1)) vuodet))

(defn tee-vuosisummat [vuodet summat]
  (let [summat (into {} (map (juxt #(int (:vuosi %)) :summa)) summat)]
    (mapv (fn [vuosi]
            (let [summa (get summat (time/year (coerce/from-date (:loppupvm vuosi))) 0)]
              (rakenna-vuosi vuosi summa)))
          vuodet)))

(defn tee-vuosittaiset-summat [db numero maksueran-tiedot]
  (let [vuodet (mapv (fn [vuosi]
                       {:alkupvm (first vuosi)
                        :loppupvm (second vuosi)})
                     (pvm/urakan-vuodet (konv/java-date (:alkupvm (:toimenpideinstanssi maksueran-tiedot)))
                                        (konv/java-date (:loppupvm (:toimenpideinstanssi maksueran-tiedot)))))]
    (case (:tyyppi (:maksuera maksueran-tiedot))
      "kokonaishintainen" (tee-vuosisummat vuodet (kustannussuunnitelmat/hae-kustannussuunnitelman-kokonaishintaiset-summat db numero))
      "yksikkohintainen" (tee-vuosisummat vuodet (kustannussuunnitelmat/hae-kustannussuunnitelman-yksikkohintaiset-summat db numero))
      (tee-oletus-vuosisummat vuodet))))

(defn valitse-lkp-tilinumero [numero toimenpidekoodi tuotenumero]
  (if (or (= toimenpidekoodi "20112") (= toimenpidekoodi "20143") (= toimenpidekoodi "20179"))
    "43020000"
    ; Hoitotuotteet 110 - 150, 536
    (if (nil? tuotenumero)
      (let [viesti (format "Tuotenumero on tyhjä. LPK-tilinnumeroa ei voi päätellä. Kustannussuunnitelman lähetys epäonnistui (numero %s)." numero)]
        (log/error viesti)
        (throw+ {:type :virhe-sampo-kustannussuunnitelman-lahetyksessa
                 :virheet [{:koodi :lpk-tilinnumeroa-ei-voi-paatella
                            :viesti viesti}]}))

      (if (or (and (>= tuotenumero 110) (<= tuotenumero 150)) (= tuotenumero 536) (= tuotenumero 31))
        "43020000"
        ; Ostotuotteet: 210, 240-271 ja 310-321
        (if (or (= tuotenumero 21)
                (= tuotenumero 30)
                (= tuotenumero 210)
                (and (>= tuotenumero 240) (<= tuotenumero 271))
                (and (>= tuotenumero 310) (<= tuotenumero 321)))
          "12980010"
          (let [viesti
                (format "Toimenpidekoodilla '%1$s' ja tuonenumerolla '%2$s' ei voida päätellä LKP-tilinnumeroa kustannussuunnitelmalle (numero: %s)."
                        toimenpidekoodi tuotenumero numero)]
            (log/error viesti)
            (throw+ {:type :virhe-sampo-kustannussuunnitelman-lahetyksessa
                     :virheet [{:koodi :lpk-tilinnumeroa-ei-voi-paatella
                                :viesti viesti}]})))))))

(defn hae-maksueran-tiedot [db numero]
  (let [maksueran-tiedot (konversio/alaviiva->rakenne (first (maksuerat/hae-lahetettava-maksuera db numero)))
        vuosittaiset-summat (tee-vuosittaiset-summat db numero maksueran-tiedot)
        lkp-tilinnumero (valitse-lkp-tilinumero numero (:toimenpidekoodi maksueran-tiedot) (:tuotenumero maksueran-tiedot))
        maksueran-tiedot (assoc maksueran-tiedot :vuosittaiset-summat vuosittaiset-summat :lkp-tilinumero lkp-tilinnumero)]
    maksueran-tiedot))

(defn tee-kustannusuunnitelma-jms-lahettaja [sonja integraatioloki db jono]
  (jms/jonolahettaja (integraatioloki/lokittaja integraatioloki db "sampo" "kustannussuunnitelma-lahetys") sonja jono))

(defn laheta-kustannussuunitelma [sonja integraatioloki db lahetysjono-ulos numero]
  (log/debug (format "Lähetetään kustannussuunnitelma (numero: %s) Sampoon." numero))
  (if (kustannussuunnitelmat/onko-olemassa? db numero)
    (if (lukitse-kustannussuunnitelma db numero)
      (let [jms-lahettaja (tee-kustannusuunnitelma-jms-lahettaja sonja integraatioloki db lahetysjono-ulos)
            maksuera (hae-maksueran-tiedot db numero)
            muodosta-xml #(kustannussuunitelma-sanoma/kustannussuunnitelma-xml maksuera)]
        (try
          (let [viesti-id (jms-lahettaja muodosta-xml nil)]
            (merkitse-kustannussuunnitelma-odottamaan-vastausta db numero viesti-id)
            (log/debug (format "Kustannussuunnitelma (numero: %s) merkittiin odottamaan vastausta." numero)))
          (catch Exception e
            (log/error e (format "Kustannussuunnitelman (numero: %s) lähetyksessä Sonjaan tapahtui poikkeus: %s." numero e))
            (merkitse-kustannussuunnitelmalle-lahetysvirhe db numero)
            (throw e))))
      (log/warn (format "Kustannusuunnitelman (numero: %s) lukitus epäonnistui." numero)))
    (let [virheviesti (format "Tuntematon kustannussuunnitelma (numero: %s)" numero)]
      (log/error virheviesti)
      (throw+ {:type virheet/+tuntematon-kustannussuunnitelma+
               :virheet [{:koodi :tuntematon-kustannussuunnitelma :viesti virheviesti}]}))))

(defn kasittele-kustannussuunnitelma-kuittaus [db kuittaus viesti-id]
  (jdbc/with-db-transaction [db db]
    (if-let [maksuera (hae-kustannussuunnitelman-maksuera db viesti-id)]
      (if (contains? kuittaus :virhe)
        (do
          (log/error "Vastaanotettiin virhe Sampon kustannussuunnitelmalähetyksestä: " kuittaus)
          (merkitse-kustannussuunnitelmalle-lahetysvirhe db maksuera))
        (merkitse-kustannussuunnitelma-lahetetyksi db maksuera))
      (log/error "Viesti-id:llä " viesti-id " ei löydy kustannussuunnitelmaa."))))
