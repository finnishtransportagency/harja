(ns harja.palvelin.integraatiot.sampo.kasittely.maksuerat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [hiccup.core :refer [html]]
            [harja.kyselyt.maksuerat :as qm]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.integraatiot.sampo.sanomat.maksuera_sanoma :as maksuera-sanoma]
            [harja.kyselyt.toimenpideinstanssit :as toimenpideinstanssit]
            [harja.kyselyt.maksuerat :as maksuerat]
            [harja.kyselyt.kustannussuunnitelmat :as kustannussuunnitelmat]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.integraatiopisteet.jms :as jms]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+]])
  (:import (java.util UUID)))

(def maksueratyypit ["kokonaishintainen" "yksikkohintainen" "lisatyo" "indeksi" "bonus" "sakko" "akillinen-hoitotyo" "muu"])
(def maksueratyypit-maanteidenhoidon-urakoissa ["kokonaishintainen"]) ;; MHU = maanteiden hoidon urakka = teiden-hoito -urakkatyyppi = uudenlainen hoidon urakka = kaikki uudet hoitourakat lokakuusta 2019 lähtien

(defn hae-maksuera [db numero summat]
  (let [maksuera (konversio/alaviiva->rakenne (first (qm/hae-lahetettava-maksuera db numero)))
        tpi (get-in maksuera [:toimenpideinstanssi :id])
        tyyppi (keyword (get-in maksuera [:maksuera :tyyppi]))
        maksueran-summat (first (filter #(= (:tpi_id %) tpi) summat))]
    (assoc-in maksuera [:maksuera :summa] (get maksueran-summat tyyppi))))

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

(defn tee-makseuran-nimi [toimenpiteen-nimi maksueratyyppi]
  (let [tyyppi (case maksueratyyppi
                 "kokonaishintainen" "Kokonaishintaiset"
                 "yksikkohintainen" "Yksikköhintaiset"
                 "lisatyo" "Lisätyöt"
                 "indeksi" "Indeksit"
                 "bonus" "Bonukset"
                 "sakko" "Sakot"
                 "akillinen-hoitotyo" "Äkilliset hoitotyöt"
                 "Muut")]
    (str toimenpiteen-nimi ": " tyyppi)))

;; Jos Samposta tulee toimenpideinstanssi, joka jo on kannassa, päivitetään siihen liityvät maksuerät ja kustannussuunnitelmat
;; likaisiksi, jotta ne lähetetään uudelleen Sampoon. Tämä sen varalta, että toimenpideinstansissa on merkitsevä muutos (esim. tuotepolku).
(defn paivita-toimenpiteen-maksuerat-ja-kustannussuunnitelmat-likaisiksi
  [db tpi-id]
  (maksuerat/merkitse-toimenpiteen-maksuerat-likaisiksi! db tpi-id)
  (kustannussuunnitelmat/merkitse-toimenpiteen-kustannussunnitelmat-likaisiksi! db tpi-id))

(defn perusta-maksuerat-hoidon-urakoille [db]
  (log/debug "Perustetaan maksuerät hoidon maksuerättömille toimenpideinstansseille")
  (let [maksuerattomat-tpit (toimenpideinstanssit/hae-hoidon-maksuerattomat-toimenpideistanssit db)]
    (if (empty? maksuerattomat-tpit)
      (log/debug "Kaikki maksuerät on jo perustettu urakoiden toimenpiteille"))
    (doseq [tpi maksuerattomat-tpit]
      (doseq [maksueratyyppi (if (= (:urakkatyyppi tpi) "teiden-hoito")
                               maksueratyypit-maanteidenhoidon-urakoissa
                               maksueratyypit)]
        (let [maksueran-nimi (tee-makseuran-nimi (:toimenpide_nimi tpi) maksueratyyppi)
              maksueranumero (:numero (maksuerat/luo-maksuera<! db (:toimenpide_id tpi) maksueratyyppi maksueran-nimi))]
          (kustannussuunnitelmat/luo-kustannussuunnitelma<! db maksueranumero))))))

(defn tarkista-maksueran-tiedot [{:keys [toimenpideinstanssi numero]}]
  (when (str/blank? (:talousosastopolku toimenpideinstanssi))
    (let [virheviesti (format "Maksuerältä (numero: %s) puuttuu talousosastopolku. Maksuerää ei voi lähettää." numero)]
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi :puuttuva-talousosastopolku :viesti virheviesti}]})))
  (when (str/blank? (:tuotepolku toimenpideinstanssi))
    (let [virheviesti (format "Maksuerältä (numero: %s) puuttuu tuotepolku. Maksuerää ei voi lähettää." numero)]
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi :puuttuva-tuotepolku :viesti virheviesti}]}))))

(defn hae-maksueran-tiedot [db numero summat]
  (let [maksueran-tiedot (hae-maksuera db numero summat)
        ;; Sakot lähetetään Sampoon negatiivisena
        maksueran-tiedot (if (= (:tyyppi (:maksuera maksueran-tiedot)) "sakko")
                           (update-in maksueran-tiedot [:maksuera :summa]
                                      #(if %
                                         (if (> % 0)
                                           (- %)
                                           %)
                                         0))
                           maksueran-tiedot)]
    maksueran-tiedot))

(defn tee-maksuera-jms-lahettaja [sonja integraatioloki db jono]
  (jms/jonolahettaja (integraatioloki/lokittaja integraatioloki db "sampo" "maksuera-lahetys") sonja jono))

(defn laheta-maksuera [sonja integraatioloki db lahetysjono-ulos numero summat]
  (log/debug (format "Lähetetään maksuera (numero: %s) Sampoon." numero))
  (if (maksuerat/onko-olemassa? db numero)
    (try
      (if (lukitse-maksuera db numero)
        (let [jms-lahettaja (tee-maksuera-jms-lahettaja sonja integraatioloki db lahetysjono-ulos)
              muodosta-sanoma (fn []
                                (let [maksuera (hae-maksueran-tiedot db numero summat)]
                                  (tarkista-maksueran-tiedot maksuera)
                                  (maksuera-sanoma/maksuera-xml maksuera)))]

          (let [viesti-id (jms-lahettaja muodosta-sanoma nil)]
            (merkitse-maksuera-odottamaan-vastausta db numero viesti-id)
            (log/debug (format "Maksuerä (numero: %s) merkittiin odottamaan vastausta." numero))))
        (log/warn (format "Maksuerän (numero: %s) lukitus epäonnistui." numero)))

      (catch Exception e
        (log/warn e (format "Maksuerän (numero: %s) lähetyksessä Sonjaan tapahtui poikkeus: %s." numero e))
        (merkitse-maksueralle-lahetysvirhe db numero)
        (throw e)))

    (let [virheviesti (format "Tuntematon maksuera (numero: %s)" numero)]
      (log/error virheviesti)
      (throw+ {:type virheet/+tuntematon-maksuera+
               :virheet [{:koodi :tuntematon-maksuera :viesti virheviesti}]}))))

(defn kasittele-maksuera-kuittaus [db kuittaus viesti-id]
  (jdbc/with-db-transaction [db db]
    (if-let [maksueranumero (hae-maksueranumero db viesti-id)]
      (if (contains? kuittaus :virhe)
        (do
          (log/error "Vastaanotettiin virhe Sampon maksuerälähetyksestä: " kuittaus)
          (merkitse-maksueralle-lahetysvirhe db maksueranumero))
        (merkitse-maksuera-lahetetyksi db maksueranumero))
      (log/warn "Viesti-id:llä " viesti-id " ei löydy maksuerää."))))
