(ns harja.palvelin.ajastetut-tehtavat.vaylien-geometriat
  (:require [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.geometriapaivitykset :as q-geometriapaivitykset]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [cheshire.core :as cheshire]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.vaylat :as q-vaylat]
            [harja.pvm :as pvm]))

(def geometriapaivitystunnus "vaylat")
(defn paivitys-tarvitaan? [db paivitysvali-paivissa]
  (let [viimeisin-paivitys (c/from-sql-time
                            (:viimeisin_paivitys
                             (first (q-geometriapaivitykset/hae-paivitys db geometriapaivitystunnus))))]
    (or (nil? viimeisin-paivitys)
        (>= (pvm/paivia-valissa viimeisin-paivitys (pvm/nyt-suomessa)) paivitysvali-paivissa))))

(defn paattele-vaylalaji [teksti]
  (if (and teksti
           (re-matches #"^VL[12].*" teksti))
    "kauppamerenkulku"
    ;; else
    "muu"))

(defn tallenna-vayla! [db {:keys [id geometry properties]}]
  ;; Tietosisällön kuvaus löytyy http://docplayer.fi/20620576-Vesivaylaaineistojen-tietosisallon-kuvaus.html

  (let [nimi (:VAY_NIMISU properties)
        tyyppi (paattele-vaylalaji (:VAYLA_LK properties))
        vaylanro (:JNRO properties)
        arvot (cheshire/encode properties)
        sql-parametrit {:sijainti (cheshire/encode geometry)
                        :nimi nimi
                        :tunniste id
                        :vaylanro vaylanro
                        :tyyppi tyyppi
                        :arvot arvot}]

    (when nimi
      (q-vaylat/luo-tai-paivita-vayla<! db sql-parametrit))))

(defn kasittele-vaylat [db vastaus]
  (let [data (cheshire/decode vastaus keyword)
        vaylat (get data :features)]
    (jdbc/with-db-transaction [db db]
      (doseq [vayla vaylat]
        (tallenna-vayla! db vayla))
      (q-geometriapaivitykset/paivita-viimeisin-paivitys db geometriapaivitystunnus (pvm/nyt)))))

(defn paivita-vaylat [integraatioloki db url]
  (log/debug "Päivitetään väylien geometriat")
  (integraatiotapahtuma/suorita-integraatio
    db
    integraatioloki
    "inspire"
    "vaylien-haku"
    (fn [konteksti]
      (let [http-asetukset {:metodi :GET :url url}
            {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
        (kasittele-vaylat db vastaus))))
  (log/debug "Väylien päivitys tehty"))

(defn- vaylien-geometriahakutehtava [integraatioloki db url paivittainen-tarkistusaika paivitysvali-paivissa]
  (log/debug (format "Ajastetaan väylien geometrioiden haku tehtäväksi %s päivän välein osoitteesta: %s."
                     paivitysvali-paivissa
                     url))
  (when (and paivittainen-tarkistusaika paivitysvali-paivissa url)
    (ajastettu-tehtava/ajasta-paivittain
      paivittainen-tarkistusaika
      (fn [_]
        (when (paivitys-tarvitaan? db paivitysvali-paivissa)
          (paivita-vaylat integraatioloki db url))))))

(defrecord VaylienGeometriahaku [url paivittainen-tarkistusaika paivitysvali-paivissa]
  component/Lifecycle
  (start [{:keys [integraatioloki db] :as this}]
    (assoc this :vaylien-geometriahaku
                (vaylien-geometriahakutehtava
                  integraatioloki
                  db
                  url
                  paivittainen-tarkistusaika
                  paivitysvali-paivissa)))
  (stop [this]
    (when-let [lopeta-fn (:vaylien-geometriahaku this)]
      (lopeta-fn))
    this))

;; esim (harja.palvelin.ajastetut-tehtavat.vaylien-geometriat/kutsu-interaktiivisesti "https://extranet.vayla.fi/inspirepalvelu/avoin/wfs?Request=GetFeature&typename=vaylat&OUTPUTFORMAT=application/json")

(defn kutsu-interaktiivisesti [geometria-url]
  (let [j (deref (ns-resolve 'harja.palvelin.main 'harja-jarjestelma))]
    (paivita-vaylat (:integraatioloki j) (:db j) geometria-url)))
