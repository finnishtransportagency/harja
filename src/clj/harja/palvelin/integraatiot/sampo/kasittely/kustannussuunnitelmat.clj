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
  (:use [slingshot.slingshot :only [throw+ try+]])
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


(defn laske-kustannussuunnitelman-vuosisummat
  "Käytetään maanteiden hoidon urakoissa (MHU). Laskee yhteen relevantit budjettisuunnitelmat
  (kiinteahintaiset_tyot, kustannusarvioidut_tyot (tyypiltään laskutettava-tyo) ja yksikkohintaiset_tyot [sic].
  Palauttaa vuodet ja yhteenlasketut summat, joita tee-vuosisummat-funktio voi hyödyntää."
  [db tyyppi]
  true ;;TODO

  )

(defn tee-oletus-vuosisummat [vuodet]
  (map (fn [vuosi] (rakenna-vuosi vuosi 1)) vuodet))


;; Jos summa on 0 euroa, summaksi asetetaan 1 euro. Sampo-järjestelmän vaatimus.
;; Vaatimus koskee kokonaishintaisten ja yksikköhintaisten toimenpiteiden lisäksi lisä-ja muutostöitä.
(defn tee-vuosisummat [vuodet summat]
  (let [summat (into {} (map (juxt #(int (:vuosi %)) :summa)) summat)]
    (mapv (fn [vuosi]
            (let [summa (or (get summat (time/year (coerce/from-date (:loppupvm vuosi))) 1) 0)]
              (rakenna-vuosi vuosi summa)))
          vuodet)))

(defn tee-vuosittaiset-summat [db numero maksueran-tiedot]
  (let [vuodet (mapv (fn [vuosi]
                       {:alkupvm (first vuosi)
                        :loppupvm (second vuosi)})
                     (pvm/urakan-vuodet (konv/java-date (:alkupvm (:toimenpideinstanssi maksueran-tiedot)))
                                        (konv/java-date (:loppupvm (:toimenpideinstanssi maksueran-tiedot)))))
        maksueratyyppi (:tyyppi (:maksuera maksueran-tiedot))
        urakkatyyppi (get-in maksueran-tiedot [:urakka :tyyppi])]
    (case maksueratyyppi
      "kokonaishintainen" (if (= "teiden-hoito" urakkatyyppi)
                            (tee-vuosisummat vuodet (kustannussuunnitelmat/hae-kustannussuunnitelman-kokonaishintaiset-summat db numero)) ;;TODO
                            (tee-vuosisummat vuodet (kustannussuunnitelmat/hae-kustannussuunnitelman-kokonaishintaiset-summat db numero)))
      "yksikkohintainen" (tee-vuosisummat vuodet (kustannussuunnitelmat/hae-kustannussuunnitelman-yksikkohintaiset-summat db numero))
      "lisatyo" (if (contains? #{"vesivayla-kanavien-hoito" "vesivayla-kanavien-korjaus"} urakkatyyppi)
                  (tee-vuosisummat vuodet (kustannussuunnitelmat/hae-kustannussuunnitelman-yksikkohintaiset-summat db numero))
                  (tee-oletus-vuosisummat vuodet))
      "akillinen-hoitotyo" (if (= "teiden-hoito" urakkatyyppi)
                             (tee-vuosisummat vuodet (kustannussuunnitelmat/hae-kustannussuunnitelman-kokonaishintaiset-summat db numero))
                             (tee-oletus-vuosisummat vuodet)) ;;TODO
      "muu" (if (= "teiden-hoito" urakkatyyppi)
              (tee-vuosisummat vuodet (kustannussuunnitelmat/hae-kustannussuunnitelman-kokonaishintaiset-summat db numero))
              (tee-oletus-vuosisummat vuodet)) ;;TODO
      (tee-oletus-vuosisummat vuodet))))

(defn valitse-lpk-tilinumero
  [numero toimenpidekoodi]
  (case toimenpidekoodi
    "23104" "43020000" ;; Talvihoito
    "23116" "43020000" ;; Liikenneympäristön hoito
    "23124" "43020000" ;; Soratien hoito
    "20107" "43020000" ;; Päällysteiden paikkaus
    "20112" "43020000" ;; Päällystetun tien rakenne (urakkatyyppi: hoito)
    "20143" "43020000" ;; Soratien rakenne (urakkatyyppi: hoito)
    "20179" "43020000" ;; Varuste ja laite korjaus (urakkatyyppi: hoito)
    "20191" "43020000" ;; MHU Ylläpito (urakkatyyppi: teiden-hoito)
    "23151" "43020000" ;; Hallinnolliset toimenpiteet (urakkatyyppi: teiden-hoito, urakkatyypissä hoito HJU-urakat)
    "27105" "43020000" ;; Vesiliikenteen käyttöpalvelut (urakkatyyppi: kanava)
    "20106" "12980010" ;; Päällyste (urakkatyyppi: paallystys). Kustannussuunnitelmia tai maksueriä ei lähetetä.
    "20135" "12980010" ;; Tiesilta (urakkatyyppi: tiemerkinta). Kustannussuunnitelmia tai maksueriä ei lähetetä.
    "20183" "12980010" ;; Liikenneympäristön parantaminen (urakkatyyppi: hoito)
    "14109" "12980010" ;; Sorateiden runkokelirikkokorjaukset (urakkatyyppi: hoito)
    "14301" "12980010" ;; MHU Korvausinvestointi (urakkatyyppi: teiden-hoito)
    "141217" "12980010" ;; Varuste ja laite (urakkatyyppi: hoito)
    :else 0
    (let [viesti
            (format "Toimenpidekoodilla '%1$s' ei voida päätellä LKP-tilinnumeroa kustannussuunnitelmalle (numero: %s)."
                    toimenpidekoodi numero)]
        (log/warn viesti)
        (throw+ {:type :virhe-sampo-kustannussuunnitelman-lahetyksessa
                 :virheet [{:koodi :lpk-tilinnumeroa-ei-voi-paatella
                            :viesti viesti}]}))))

(defn hae-maksueran-tiedot [db numero]
  (let [maksueran-tiedot (konversio/alaviiva->rakenne (first (maksuerat/hae-lahetettava-maksuera db numero)))
        vuosittaiset-summat (tee-vuosittaiset-summat db numero maksueran-tiedot)
        lkp-tilinnumero (valitse-lpk-tilinumero numero (:toimenpidekoodi maksueran-tiedot))
        maksueran-tiedot (assoc maksueran-tiedot :vuosittaiset-summat vuosittaiset-summat :lkp-tilinumero lkp-tilinnumero)]
    maksueran-tiedot))

(defn tee-kustannusuunnitelma-jms-lahettaja [sonja integraatioloki db jono]
  (jms/jonolahettaja (integraatioloki/lokittaja integraatioloki db "sampo" "kustannussuunnitelma-lahetys") sonja jono))

(defn voi-lahettaa? [db numero]
  (if (kustannussuunnitelmat/tuotenumero-loytyy? db numero)
    true
    (do
      (log/warn (format "Kustannussuunnitelmaa (numero: %s) ei voida lähettää Sampoon. Tuotenumero puuttuu." numero))
      false)))

(defn laheta-kustannussuunitelma [sonja integraatioloki db lahetysjono-ulos numero]
  (log/debug (format "Lähetetään kustannussuunnitelma (numero: %s) Sampoon." numero))
  (if (kustannussuunnitelmat/onko-olemassa? db numero)
    (when (voi-lahettaa? db numero)
      (try+
        (if (lukitse-kustannussuunnitelma db numero)
          (let [jms-lahettaja (tee-kustannusuunnitelma-jms-lahettaja sonja integraatioloki db lahetysjono-ulos)
                muodosta-sanoma #(kustannussuunitelma-sanoma/kustannussuunnitelma-xml (hae-maksueran-tiedot db numero))]
            (let [viesti-id (jms-lahettaja muodosta-sanoma nil)]
              (merkitse-kustannussuunnitelma-odottamaan-vastausta db numero viesti-id)
              (log/debug (format "Kustannussuunnitelma (numero: %s) merkittiin odottamaan vastausta." numero))))
          (log/warn (format "Kustannusuunnitelman (numero: %s) lukitus epäonnistui." numero)))
        (catch Object e
          (log/error e (format "Kustannussuunnitelman (numero: %s) lähetyksessä Sonjaan tapahtui poikkeus: %s." numero e))
          (merkitse-kustannussuunnitelmalle-lahetysvirhe db numero))))
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
