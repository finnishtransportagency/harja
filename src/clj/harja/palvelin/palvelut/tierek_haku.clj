(ns harja.palvelin.palvelut.tierek-haku
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tieverkko :as tv]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]))

(def +threshold+ 250)

(defn muunna-geometria [tros]
  (assoc tros :geometria (geo/pg->clj (:geometria tros))))

(defn hae-tr-pisteilla
  "params on mappi {:x1 .. :y1 .. :x2 .. :y2 ..}"
  [db params]
  (when-let [tros (first (tv/hae-tr-osoite-valille db
                                                   (:x1 params) (:y1 params)
                                                   (:x2 params) (:y2 params)
                                                   +threshold+))]
    (muunna-geometria tros)))

(defn hae-tr-pisteella
  "params on mappi {:x .. :y ..}"
  [db {:keys [x y] :as params}]
  (let [tros (first (tv/hae-tr-osoite db {:x x :y y
                                          :treshold +threshold+}))]
    (muunna-geometria tros)))

(defn hae-tr-viiva
  "Params on mappi: {:numero int, :alkuosa int, :alkuetaisyys int, :loppuosa int, :loppuetaisyys int}"
  [db params]
  (log/debug "Haetaan viiva osoiteelle " (pr-str params))
  (let [korjattu-osoite params
        viiva? (and (:loppuosa korjattu-osoite)
                    (:loppuetaisyys korjattu-osoite))
        geom (geo/pg->clj
               (if viiva?
                 (tv/tierekisteriosoite-viivaksi db
                                                 (:numero korjattu-osoite)
                                                 (:alkuosa korjattu-osoite)
                                                 (:alkuetaisyys korjattu-osoite)
                                                 (:loppuosa korjattu-osoite)
                                                 (:loppuetaisyys korjattu-osoite))
                 (tv/tierekisteriosoite-pisteeksi db
                                                  (:numero korjattu-osoite)
                                                  (:alkuosa korjattu-osoite)
                                                  (:alkuetaisyys korjattu-osoite))))]
    (if geom
      [geom]
      {:virhe "Tierekisteriosoitetta ei löydy"})))

(defn hae-osien-pituudet
  "Hakee tierekisteriosien pituudet annetulle tielle ja osan välille.
   Params mäpissä tulee olla :tie, :aosa ja :losa.
   Palauttaa pituuden metreinä."
  [db params]
  (into {}
        (map (juxt :osa :pituus))
        (tv/hae-osien-pituudet db params)))

(defn hae-tieosan-ajoradat [db params]
  "Hakee annetun tien osan ajoradat. Parametri-mapissa täytyy olla :tie ja :osa"
  (mapv :ajorata (tv/hae-tieosan-ajoradat db params)))

(defrecord TierekisteriHaku []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelut
      http-palvelin
      :hae-tr-pisteilla (fn [_ params]
                          (hae-tr-pisteilla db params))

      :hae-tr-pisteella (fn [_ params]
                          (hae-tr-pisteella db params))

      :hae-tr-viivaksi (fn [_ params]
                         (hae-tr-viiva db params))

      :hae-tr-osien-pituudet (fn [_ params]
                               (hae-osien-pituudet db params))

      :hae-tr-osan-ajoradat (fn [_ params]
                              (hae-tieosan-ajoradat db params)))

    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-tr-pisteilla
                     :hae-tr-pisteella
                     :hae-tr-viivaksi
                     :hae-osien-pituudet
                     :hae-tr-osan-ajoradat)
    this))
