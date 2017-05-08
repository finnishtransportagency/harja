(ns harja.palvelin.ajastetut-tehtavat.turvalaitteiden-geometriat
  (:require [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [harja.kyselyt.geometriapaivitykset :as q-geometriapaivitykset]
            [harja.pvm :as pvm]
            [clj-time.coerce :as c]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [cheshire.core :as cheshire]
            [clojure.walk :as walk]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.turvalaitteet :as q-turvalaitteet]
            [harja.geo :as geo])
  (:import (org.postgis Point)))

(def geometriapaivitystunnus "turvalaitteet")
(defn paivitys-tarvitaan? [db paivitysvali-paivissa]
  (let [viimeisin-paivitys (c/from-sql-time
                             (:viimeisin_paivitys
                               (first (q-geometriapaivitykset/hae-paivitys db geometriapaivitystunnus))))]
    (or (nil? viimeisin-paivitys)
        (>= (pvm/paivia-valissa viimeisin-paivitys (pvm/nyt-suomessa)) paivitysvali-paivissa))))

(defn tallenna-turvalaite [db {:keys [id geometry properties]}]
  (let [koordinaatit (:coordinates geometry)
        geometria (geo/geometry (Point. (first koordinaatit) (second koordinaatit)))
        arvot (cheshire/encode properties)
        sql-parametrit {:sijainti geometria
                        :tunniste id
                        :arvot arvot}]
    (q-turvalaitteet/luo-turvalaite<! db sql-parametrit)))

(defn kasittele-turvalaitteet [db vastaus]
  (let [data (cheshire/decode vastaus keyword)
        turvalaitteet (get data :features)]
    (jdbc/with-db-transaction [db db]
      (q-turvalaitteet/poista-turvalaitteet! db)
      (doseq [turvalaite turvalaitteet]
        (tallenna-turvalaite db turvalaite))
      (q-geometriapaivitykset/paivita-viimeisin-paivitys db geometriapaivitystunnus (harja.pvm/nyt)))))

(defn paivita-turvalaitteet [integraatioloki db url]
  (log/debug "Päivitetään turvalaitteiden geometriat")
  (integraatiotapahtuma/suorita-integraatio
    db
    integraatioloki
    "inspire"
    "turvalaitteiden-haku"
    (fn [konteksti]
      (let [http-asetukset {:metodi :GET :url url}
            {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
        (kasittele-turvalaitteet db vastaus))))
  (log/debug "Turvalaitteidein päivitys tehty"))

(defn- turvalaitteiden-geometriahakutehtava [integraatioloki db url paivittainen-tarkistusaika paivitysvali-paivissa]
  (log/debug (format "Ajastetaan turvalaitteiden geometrioiden haku tehtäväksi %s päivän välein osoitteesta: %s."
                     paivitysvali-paivissa
                     url))

  (when (and paivittainen-tarkistusaika paivitysvali-paivissa url)
    (ajastettu-tehtava/ajasta-paivittain
     paivittainen-tarkistusaika
     (fn [_]
       (when (paivitys-tarvitaan? db paivitysvali-paivissa)
         (paivita-turvalaitteet integraatioloki db url))))))

(defrecord TurvalaitteidenGeometriahaku [url paivittainen-tarkistusaika paivitysvali-paivissa]
  component/Lifecycle
  (start [{:keys [integraatioloki db] :as this}]
    (assoc this :turvalaitteiden-geometriahaku
                (turvalaitteiden-geometriahakutehtava
                  integraatioloki
                  db
                  url
                  paivittainen-tarkistusaika
                  paivitysvali-paivissa)))
  (stop [this]
    (when-let [lopeta-fn (:turvalaitteiden-geometriahaku this)]
      (lopeta-fn))
    this))
