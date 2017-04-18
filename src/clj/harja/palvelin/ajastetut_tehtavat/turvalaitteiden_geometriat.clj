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
            [clojure.string :as string]))

(defn paivitys-tarvitaan? [db paivitysvali-paivissa]
  (let [viimeisin-paivitys (c/from-sql-time
                             (:viimeisin_paivitys
                               (first (q-geometriapaivitykset/hae-paivitys db "turvalaitteet"))))]
    (or (nil? viimeisin-paivitys)
        (>= (pvm/paivia-valissa viimeisin-paivitys (pvm/nyt-suomessa)) paivitysvali-paivissa))))

(defn tallenna-turvalaite [db {:keys [id geometry properties] :as turvalaite}]
  (println "---> turvalaite" turvalaite)
  (let [geometria (geo/clj->pg (assoc geometry :type (keyword (string/lower-case (name (:type geometry))))))
        {:keys [NIMIR SUBTYPE SIJAINTS VAYLAT TILA]} properties
        sql-parametrit {:sijainti geometria
                        :tunniste id
                        :nimi NIMIR
                        :alityyppi SUBTYPE
                        :sijainnin_kuvaus SIJAINTS
                        :vayla VAYLAT
                        :tila TILA}]
    (q-turvalaitteet/luo-turvalaite<! db sql-parametrit))
  ;; Saatavilla olevat arvot: TUTKAHEIJ , SIJAINTIR , NAVL_TYYP , TLNUMERO , FASADIVALO , OMISTAJA , PATA_TYYP , NIMIR , SUBTYPE , TY_JNR , RAK_VUOSI , PAIV_PVM , SIJAINTIS , VAYLAT , PAKO_TYYP , MITT_PVM , VALAISTU , RAKT_TYYP , IRROTUS_PVM , NIMIS , TKLNUMERO , TILA , HUIPPUMERK , TOTI_TYYP , VAHV_PVM
  )

(defn kasittele-vastaus [db vastaus]
  (let [data (cheshire/decode vastaus)
        turvalaitteet (get data "features")]
    (doseq [turvalaite (take 10 turvalaitteet)]
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