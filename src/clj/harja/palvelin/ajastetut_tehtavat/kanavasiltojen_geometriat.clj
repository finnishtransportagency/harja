(ns harja.palvelin.ajastetut-tehtavat.kanavasiltojen-geometriat
  (:require [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.geometriapaivitykset :as q-geometriapaivitykset]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [cheshire.core :as cheshire]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.kanavat.kanavasillat :as q-kanavasillat]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as str])
  (:import (org.postgis Point)))

(def geometriapaivitystunnus "kanavasillat") ;; Kanavienhaku hakee sulut ava:sta. Tämä täydentää
(defn paivitys-tarvitaan? [db paivitysvali-paivissa]
  (let [viimeisin-paivitys (c/from-sql-time
                             (:viimeisin_paivitys
                               (first (q-geometriapaivitykset/hae-paivitys db geometriapaivitystunnus))))]
    (or (nil? viimeisin-paivitys)
        (>= (pvm/paivia-valissa viimeisin-paivitys (pvm/nyt-suomessa)) paivitysvali-paivissa))))


;(defn tallenna-kanavasilta [db {:keys [id geometry properties]}]
(defn tallenna-kanavasilta [db kanavasilta]
  ;; TREXin (= taitorakennerekisteri) rajapinnan kuvaus on liitetty tikettiin HAR-6948
  (log/debug (str "Kanavasiltojen päivitys" kanavasilta))

  ;(let [koordinaatit (:coordinates geometry)
  ;      geometria (geo/geometry (Point. (first koordinaatit) (second koordinaatit)))
  ;      arvot (cheshire/encode properties)
  ;      vaylat (when (:VAYLAT properties) (konv/seq->array (map #(Integer. %) (str/split (:VAYLAT properties) #","))))
  ;      tyyppi (turvalaitetyyppi (:TY_JNR properties))
  ;      turvalaitenro (:TLNUMERO properties)
  ;      kiintea? (= "KIINTE" (:SUBTYPE properties))
  ;      nimi (:NIMIS properties)
  ;      sql-parametrit {:sijainti geometria
  ;                      :tyyppi tyyppi
  ;                      :turvalaitenro turvalaitenro
  ;                      :kiintea kiintea?
  ;                      :nimi nimi
  ;                      :arvot arvot
  ;                      :vaylat vaylat}]
  ;  (q-turvalaitteet/luo-turvalaite<! db sql-parametrit))
  ;
  )



(defn kasittele-kanavasillat [db vastaus]
  (log/debug "KÄSITELLÄÄN KANAVASILLAT KOSKA VASTAUS ON SAATU ONNISTUNEESTI")
  (let [data (cheshire/decode vastaus keyword)
        kanavasillat (get data :features)]
    (jdbc/with-db-transaction [db db]
      (q-kanavasillat/poista-kanavasillat! db)
      (doseq [kanavasilta kanavasillat]
        (tallenna-kanavasilta db kanavasilta))
      (q-geometriapaivitykset/paivita-viimeisin-paivitys db geometriapaivitystunnus (harja.pvm/nyt)))))

;; TODO: Toimii: ajastuksen käynnistyminen, TREXin kutsuminen, palautuu ensimmäinen sivu (1000 siltaa), kan_sillat-taulu on olemassa
;; TODO: Huomio trex pään sivutus. 1000 siltaa maksimissaan kerrallaan, 25000 siltaa palautuu eli vähintään 25 kutsua
;; TODO: Parametrisoi palautettavien siltojen määrä, jotta kuormaa voidaan hallita ilman koodimuutoksia
;; TODO: Karsi siltoja, vain avautuvat maantie- ja rautatiesillat tarvitaan
;; Teräksinen nostosilta, teräskantinen
;; Teräksinen kääntösilta, puukantinen”
;; nosto ja kääntö is the thing
;; Teräksinen läppäsilta, teräskantinen
;; NOSTO, LÄPPÄ, KÄÄNTÖ rakennetiedossa = silta on relevantti - vertaa pdf:ään lopputulosta
;; TODO: Hanki tunnukset/oikeudet oag:n kautta testi- ja tuontatoympäristössä tehtäviä kutsuja varten

(defn paivita-kanavasillat [integraatioloki db url]
  (log/debug "Päivitetään kanavasiltojen geometriat")
  (integraatiotapahtuma/suorita-integraatio
    db
    integraatioloki
    "trex"
    "kanavasillat-haku"
    (fn [konteksti]
      (let [http-asetukset {:metodi :GET :url url}
            {vastaus :body} (try
                              (integraatiotapahtuma/laheta konteksti :http http-asetukset)
                              (catch Exception e))]
                   (if vastaus
          (kasittele-kanavasillat db vastaus)
          (log/debug "Kanavasiltoja ei käsitelty, vastausta ei saatu")))))
  (log/debug "Kanavasiltojen päivitys tehty"))

(defn- kanavasiltojen-geometriahakutehtava [integraatioloki db url paivittainen-tarkistusaika paivitysvali-paivissa]
  (log/debug (format "Ajastetaan kanavasiltojen geometrioiden haku tehtäväksi %s päivän välein osoitteesta: %s."
                     paivitysvali-paivissa
                     url))

  (when (and paivittainen-tarkistusaika paivitysvali-paivissa url)
    (ajastettu-tehtava/ajasta-paivittain
      paivittainen-tarkistusaika
      (fn [_]
        (when (paivitys-tarvitaan? db paivitysvali-paivissa)
          (paivita-kanavasillat integraatioloki db url))))))

(defrecord KanavasiltojenGeometriahaku [url paivittainen-tarkistusaika paivitysvali-paivissa]
  component/Lifecycle
  (start [{:keys [integraatioloki db] :as this}]
    (log/debug "kanavasiltojen geometriahaku-komponentti käynnistyy")
    (assoc this :kanavasiltojen-geometriahaku
                (kanavasiltojen-geometriahakutehtava
                  integraatioloki
                  db
                  url
                  paivittainen-tarkistusaika
                  paivitysvali-paivissa)))
  (stop [this]
    (when-let [lopeta-fn (:kanavasiltojen-geometriahaku this)]
      (lopeta-fn))
    this))
