(ns harja.palvelin.palvelut.tierek-haku
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tieverkko :as tv]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]))

(def +treshold+ 250)

(defn hae-tr-pisteilla
  "params on mappi {:x1 .. :y1 .. :x2 .. :y2 ..}"
  [db user params]
  (when-let [tros (first (tv/hae-tr-osoite-valille db
                                                   (:x1 params) (:y1 params)
                                                   (:x2 params) (:y2 params)
                                                   +treshold+))]
    (assoc tros :geometria (geo/pg->clj (:geometria tros)))))

(defn hae-tr-pisteella
  "params on mappi {:x .. :y ..}"
  [db user params]
  (first (tv/hae-tr-osoite db (:x params) (:y params) +treshold+)))

(defn jarjestele-tr-osoite [osoite]
  (let [aosa (:alkuosa osoite)
        losa (:loppuosa osoite)
        alkuet (:alkuetaisyys osoite)
        loppuet (:loppuetaisyys osoite)]
    (if (> aosa losa)
      (assoc osoite
             :alkuosa losa
             :loppuosa aosa)
      (if (= aosa losa)
        (assoc osoite
               :alkuetaisyys (min alkuet loppuet)
               :loppuetaisyys (max alkuet loppuet))
        osoite))))

(defn hae-tr-viiva
  "params on mappi {:tie .. :aosa .. :aet .. :losa .. :let"
  [db user params]
  (let [korjattu-osoite (jarjestele-tr-osoite params)
        geom (first (tv/tierekisteriosoite-viivaksi db
                                                    (:numero korjattu-osoite)
                                                    (:alkuosa korjattu-osoite)
                                                    (:alkuetaisyys korjattu-osoite)
                                                    (:loppuosa korjattu-osoite)
                                                    (:loppuetaisyys korjattu-osoite)))]
    (log/debug "hae-tr-viiva " geom)
    (geo/pg->clj (:tierekisteriosoitteelle_viiva geom))))

(defn hae-tr-piste
  "params on mappi {:tie .. :aosa .. :aet .. :losa .. :let"
  [db user params]
  (log/debug "Haetaan piste osoitteelle: " (pr-str params))
  (let [geom (first (tv/tierekisteriosoite-pisteeksi db
                                                    (:numero params)
                                                    (:alkuosa params)
                                                    (:alkuetaisyys params)))]
    (geo/pg->clj (:tierekisteriosoitteelle_piste geom))))

(defrecord TierekisteriHaku []
  component/Lifecycle
  (start [this]
    (julkaise-palvelu (:http-palvelin this)
                      :hae-tr-pisteilla (fn [user params]
                                          (hae-tr-pisteilla (:db this) user params)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-tr-pisteella (fn [user params]
                                          (hae-tr-pisteella (:db this) user params)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-tr-viivaksi (fn [user params]
                                         (hae-tr-viiva (:db this) user params)))
    (julkaise-palvelu (:http-palvelin this)
                      :hae-tr-pisteeksi (fn [user params]
                                         (hae-tr-piste (:db this) user params)))
    this)
  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-tr-pisteella)
    this))
