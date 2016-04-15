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
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.kyselyt.konversio :as konv])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (java.util UUID Calendar TimeZone)))

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
  (map #(hash-map :alkupvm (pvm/aika-iso8601 (:alkupvm %)),
                  :loppupvm (pvm/aika-iso8601 (:loppupvm %)),
                  :summa 1) vuodet))

(defn aseta-pvm [pvm vuosi kuukausi paiva]
  (.setTime pvm vuosi)
  (.set pvm Calendar/MONTH kuukausi)
  (.set pvm Calendar/DAY_OF_MONTH paiva)
  (.setTimeZone pvm (TimeZone/getTimeZone "EET")))

(defn tee-vuosisummat [vuodet summat]
  (let [summat (into {} (map (juxt #(int (:vuosi %)) :summa)) summat)]
    (mapv (fn [vuosi]
            (let [summa (get summat (time/year (coerce/from-date (:loppupvm vuosi))) 0)
                  alku (Calendar/getInstance)
                  loppu (Calendar/getInstance)]
              (aseta-pvm alku (:alkupvm vuosi) Calendar/JANUARY 1)
              (aseta-pvm loppu (:loppupvm vuosi) Calendar/DECEMBER 31)
              {:alkupvm (pvm/aika-iso8601 (.getTime alku))
               :loppupvm (pvm/aika-iso8601 (.getTime loppu))
               :summa summa}))
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

(defn valitse-lkp-tilinumero [toimenpidekoodi tuotenumero]
  (if (or (= toimenpidekoodi "20112") (= toimenpidekoodi "20143") (= toimenpidekoodi "20179"))
    "43020000"
    ; Hoitotuotteet 110 - 150, 536
    (if (nil? tuotenumero)
      (let [viesti "Tuotenumero on tyhjä. LPK-tilinnumeroa ei voi päätellä. Kustannussuunnitelman lähetys epäonnistui."]
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
                (format "Toimenpidekoodilla '%1$s' ja tuonenumerolla '%2$s' ei voida päätellä LKP-tilinnumeroa kustannussuunnitelmalle"
                        toimenpidekoodi tuotenumero)]
            (log/error viesti)
            (throw+ {:type :virhe-sampo-kustannussuunnitelman-lahetyksessa
                     :virheet [{:koodi :lpk-tilinnumeroa-ei-voi-paatella
                                :viesti viesti}]})))))))

(defn muodosta-kustannussuunnitelma [db numero]
  (let [maksueran-tiedot (maksuera/hae-maksuera db numero)
        vuosittaiset-summat (tee-vuosittaiset-summat db numero maksueran-tiedot)
        lkp-tilinnumero (valitse-lkp-tilinumero (:toimenpidekoodi maksueran-tiedot) (:tuotenumero maksueran-tiedot))
        maksueran-tiedot (assoc maksueran-tiedot :vuosittaiset-summat vuosittaiset-summat :lkp-tilinumero lkp-tilinnumero)
        kustannussuunnitelma-xml (tee-xml-sanoma (kustannussuunitelma-sanoma/muodosta maksueran-tiedot))]
    (if (xml/validoi +xsd-polku+ "nikuxog_costPlan.xsd" kustannussuunnitelma-xml)
      kustannussuunnitelma-xml
      (do
        (log/error "Kustannussuunnitelmaa ei voida lähettää. Kustannussuunnitelma XML ei ole validi.")
        nil))))

(defn laheta-kustannussuunitelma [sonja integraatioloki db lahetysjono-ulos numero]
  (log/debug "Lähetetään kustannussuunnitelma (numero: " numero ") Sampoon.")
  (let [tapahtuma-id (integraatioloki/kirjaa-alkanut-integraatio integraatioloki "sampo" "kustannussuunnitelma-lahetys" nil nil)]
    (try
      (if (lukitse-kustannussuunnitelma db numero)
        (if-let [kustannussuunnitelma-xml (muodosta-kustannussuunnitelma db numero)]
          (if-let [viesti-id (sonja/laheta sonja lahetysjono-ulos kustannussuunnitelma-xml)]
            (do
              (integraatioloki/kirjaa-jms-viesti integraatioloki tapahtuma-id viesti-id "ulos" kustannussuunnitelma-xml)
              (merkitse-kustannussuunnitelma-odottamaan-vastausta db numero viesti-id))
            (do
              (log/error "Kustannussuunnitelman (numero: " numero ") lähetys Sonjaan epäonnistui.")
              (integraatioloki/kirjaa-epaonnistunut-integraatio
                integraatioloki (str "Kustannussuunnitelman (numero: " numero ") lähetys Sonjaan epäonnistui.") nil tapahtuma-id nil)
              (merkitse-kustannussuunnitelmalle-lahetysvirhe db numero)
              {:virhe :sonja-lahetys-epaonnistui}))
          (do
            (log/warn "Kustannussuunnitelman (numero: " numero ") sanoman muodostus epäonnistui.")
            (merkitse-kustannussuunnitelmalle-lahetysvirhe db numero)
            {:virhe :kustannussuunnitelman-lukitseminen-epaonnistui}))
        (do
          (log/warn "Kustannussuunnitelman (numero: " numero ") lukitseminen epäonnistui.")
          {:virhe :kustannussuunnitelman-lukitseminen-epaonnistui}))
      (catch Exception e
        (log/error e "Sampo maksuerälähetyksessä tapahtui poikkeus.")
        (merkitse-kustannussuunnitelmalle-lahetysvirhe db numero)
        (integraatioloki/kirjaa-epaonnistunut-integraatio
          integraatioloki
          "Sampo kustannussuunnitelmalähetyksessä tapahtui poikkeus"
          (str "Poikkeus: " (.getMessage e))
          tapahtuma-id
          nil)
        {:virhe :poikkeus}))))

(defn kasittele-kustannussuunnitelma-kuittaus [db kuittaus viesti-id]
  (jdbc/with-db-transaction [transaktio db]
    (if-let [maksuera (hae-kustannussuunnitelman-maksuera transaktio viesti-id)]
      (if (contains? kuittaus :virhe)
        (do
          (log/error "Vastaanotettiin virhe Sampon kustannussuunnitelmalähetyksestä: " kuittaus)
          (merkitse-kustannussuunnitelmalle-lahetysvirhe transaktio maksuera))
        (merkitse-kustannussuunnitelma-lahetetyksi transaktio maksuera))
      (log/error "Viesti-id:llä " viesti-id " ei löydy kustannussuunnitelmaa."))))

