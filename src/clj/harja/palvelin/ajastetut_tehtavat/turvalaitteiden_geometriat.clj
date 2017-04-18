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
            [harja.kyselyt.turvalaitteet :as q-turvalaitteet]))

(defn paivitys-tarvitaan? [db paivitysvali-paivissa]
  (let [viimeisin-paivitys (c/from-sql-time
                             (:viimeisin_paivitys
                               (first (q-geometriapaivitykset/hae-paivitys db "turvalaitteet"))))]
    (or (nil? viimeisin-paivitys)
        (>= (pvm/paivia-valissa viimeisin-paivitys (pvm/nyt-suomessa)) paivitysvali-paivissa))))

(defn tallenna-turvalaite [param1]
  )

(defn kasittele-vastaus [vastaus]
  (let [data (cheshire/decode vastaus)
        turvalaitteet (get data "features")]
    (doseq [turvalaite turvalaitteet]
      (tallenna-turvalaite (walk/keywordize-keys turvalaite)))
    (println "----> " (first turvalaitteet))))

(defn paivita-turvalaitteet [integraatioloki db url]
  (log/debug "Päivitetään turvalaitteiden geometriat")

  (jdbc/with-db-transaction [db db]
    (q-turvalaitteet/poista-turvalaitteet! db)

    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "ptj" "turvalaitteiden-haku"
      (fn [konteksti]
        (let [http-asetukset {:metodi :GET
                              :url url}
              {body :body headers :headers}
              (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-vastaus body)))))
  (log/debug "Turvalaitteidein päivitys tehty"))

(defn- turvalaitteiden-geometriahakutehtava [integraatioloki db url paivittainen-tarkistusaika paivitysvali-paivissa]
  (log/debug (format "Ajastetaan turvalaitteiden geometrioiden haku tehtäväksi %s päivän välein osoitteesta: %s."
                     paivitysvali-paivissa
                     url))

  (ajastettu-tehtava/ajasta-paivittain
    paivittainen-tarkistusaika
    (fn []
      (when (paivitys-tarvitaan? db paivitysvali-paivissa)
        (paivita-turvalaitteet integraatioloki db url)))))

(defrecord TurvalaitteidenGeometriahaku [url tarkistus aika paivittainen-tarkistusaika paivitysvali-paivissa]
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
    ((:turvalaitteiden-geometriahaku this))
    this))