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
            [harja.geo :as geo]
            [clojure.string :as string])
  (:import (org.postgis Point)))

(defn paivitys-tarvitaan? [db paivitysvali-paivissa]
  (let [viimeisin-paivitys (c/from-sql-time
                             (:viimeisin_paivitys
                               (first (q-geometriapaivitykset/hae-paivitys db "turvalaitteet"))))]
    (or (nil? viimeisin-paivitys)
        (>= (pvm/paivia-valissa viimeisin-paivitys (pvm/nyt-suomessa)) paivitysvali-paivissa))))

(defn tallenna-turvalaite [db {:keys [id geometry properties] :as turvalaite}]
  ;; Saatavilla turvalaitteelle saatavat arvot properties mapissa:
  ;; TUTKAHEIJ, SIJAINTIR, NAVL_TYYP, TLNUMERO, FASADIVALO, OMISTAJA, PATA_TYYP, NIMIR, SUBTYPE, TY_JNR, RAK_VUOSI,
  ;; PAIV_PVM, SIJAINTIS, VAYLAT, PAKO_TYYP, MITT_PVM, VALAISTU, RAKT_TYYP, IRROTUS_PVM, NIMIS, TKLNUMERO, TILA,
  ;; HUIPPUMERK, TOTI_TYYP, VAHV_PVM

  (let [koordinaatit (:coordinates geometry)
        geometria (geo/geometry (Point. (first koordinaatit) (second koordinaatit)))
        {:keys [NIMIS SUBTYPE SIJAINTS VAYLAT TILA]} properties
        sql-parametrit {:sijainti geometria
                        :tunniste id
                        :nimi NIMIS
                        :alityyppi SUBTYPE
                        :sijainnin_kuvaus SIJAINTS
                        :vayla VAYLAT
                        :tila TILA}]
    (q-turvalaitteet/luo-turvalaite<! db sql-parametrit)))

(defn kasittele-vastaus [db vastaus]
  (let [data (cheshire/decode vastaus)
        turvalaitteet (get data "features")]
    (doseq [turvalaite turvalaitteet]
      (tallenna-turvalaite db (walk/keywordize-keys turvalaite)))))

(defn paivita-turvalaitteet [integraatioloki db url]
  (log/debug "Päivitetään turvalaitteiden geometriat")

  (jdbc/with-db-transaction [transaktio db]
    (q-turvalaitteet/poista-turvalaitteet! transaktio)

    (let [hae-turvalaitteet (fn [konteksti]
                              (let [http-asetukset {:metodi :GET :url url}
                                    {vastaus :body}
                                    (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
                                (kasittele-vastaus transaktio vastaus)))]
      (integraatiotapahtuma/suorita-integraatio db integraatioloki "ptj" "turvalaitteiden-haku" hae-turvalaitteet)))
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
    ((:turvalaitteiden-geometriahaku this))
    this))