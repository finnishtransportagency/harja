(ns harja.palvelin.palvelut.tierekisteri-haku
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tieverkko :as tv]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain
             [oikeudet :as oikeudet]
             [yllapitokohde :as yllapitokohde]
             [tierekisteri :as tr-domain]])
  (:import (org.postgresql.util PSQLException)))

(def +threshold+ 250)

(defn muunna-geometria [tros]
  (assoc tros :geometria (geo/pg->clj (:geometria tros))))

(defn hae-tr-pisteilla
  "params on mappi {:x1 .. :y1 .. :x2 .. :y2 ..}"
  [db params]
  (println "hae tr pisteillä: " params)
  (try
    (when-let [tros (first (tv/hae-tr-osoite-valille db
                                                     (:x1 params) (:y1 params)
                                                     (:x2 params) (:y2 params)
                                                     +threshold+))]
     (muunna-geometria tros))
    (catch PSQLException e
      (if (= (.getMessage e) "ERROR: pisteillä ei yhteistä tietä")
        nil
        ;; else
        (throw e)))))

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

(defn hae-tr-pituudet [db params]
  ;; TODO heti, kun saadaan kaistakohtaiset pituudet, otetaan se käyttöön tässä
  (reduce (fn [m pituuden-tiedot]
            (update m (:osa pituuden-tiedot)
                    (fn [osa]
                      (assoc osa (:ajorata pituuden-tiedot) (:pituus pituuden-tiedot)))))
          {} (tv/hae-ajoratojen-pituudet db params)))

(defn hae-osien-pituudet
  "Hakee tierekisteriosien pituudet annetulle tielle ja osan välille.
   Params mäpissä tulee olla :tie, :aosa ja :losa.
   Palauttaa pituuden metreinä."
  [db params]
  (into {}
        (map (juxt :osa :pituus))
        (tv/hae-osien-pituudet db params)))

(defn hae-ajoratojen-pituudet
  "Hakee tierekisteriosien pituudet annetun tien osien ajoradoille.
   Params mäpissä tulee olla :tie, :aosa ja :losa.
   Palauttaa pituuden metreinä."
  [db params]
  (tv/hae-ajoratojen-pituudet db params))

(defn hae-tieosan-ajoradat [db params]
  "Hakee annetun tien osan ajoradat. Parametri-mapissa täytyy olla :tie ja :osa"
  (mapv :ajorata (tv/hae-tieosan-ajoradat db params)))

(defn validoi-tr-osoite-tieverkolla
  "Tarkistaa, onko annettu tieosoite validi Harjan tieverkolla. Palauttaa mapin, jossa avaimet:
  :ok?      Oliko TR-osoite validi (true / false)
  :syy      Tekstimuotoinen selitys siitä, miksi ei ole validi (voi olla myös null)"
  [db {:keys [tienumero aosa aet losa let ajorata] :as tieosoite}]
  (try
    (clojure.core/let
      [osien-pituudet (hae-osien-pituudet db {:tie tienumero
                                              :aosa aosa
                                              :losa losa})
       ajoratojen-pituudet (hae-ajoratojen-pituudet db {:tie tienumero
                                                        :aosa aosa
                                                        :losa losa})
       ajorata-olemassa? (fn [osa ajorata]
                           (or (not ajorata)
                               (some #(and (= osa (:osa %)) (= ajorata (:ajorata %))) ajoratojen-pituudet)))
       tulos {:aosa-olemassa? (tr-domain/osa-olemassa-verkolla? aosa osien-pituudet)
              :losa-olemassa? (tr-domain/osa-olemassa-verkolla? losa osien-pituudet)
              :alkuosan-ajorata-olemassa? (ajorata-olemassa? aosa ajorata)
              :loppuosan-ajorata-olemassa? (ajorata-olemassa? losa ajorata)
              :aosa-pituus-validi? (tr-domain/osan-pituus-sopiva-verkolla? aosa aet ajoratojen-pituudet)
              :losa-pituus-validi? (tr-domain/osan-pituus-sopiva-verkolla? losa let ajoratojen-pituudet)
              :geometria-validi? (some? (hae-tr-viiva db {:numero tienumero
                                                          :alkuosa aosa
                                                          :alkuetaisyys aet
                                                          :loppuosa losa
                                                          :loppuetaisyys let}))}
       kaikki-ok? (every? true? (vals tulos))]
      {:ok? kaikki-ok? :syy (cond (not (:aosa-olemassa? tulos)) (str "Alkuosaa " aosa " ei ole olemassa")
                                  (not (:losa-olemassa? tulos)) (str "Loppuosaa " losa " ei ole olemassa")
                                  (not (:alkuosan-ajorata-olemassa? tulos)) (str "Alkuosan " aosa " ajorataa " ajorata " ei ole olemassa")
                                  (not (:loppuosan-ajorata-olemassa? tulos)) (str "Loppuosan " losa " ajorataa " ajorata " ei ole olemassa")
                                  (not (:aosa-pituus-validi? tulos)) (str "Alkuosan pituus " aet " ei kelpaa")
                                  (not (:losa-pituus-validi? tulos)) (str "Loppuosan pituus " let " ei kelpaa")
                                  (not (:geometria-validi? tulos)) "Osoitteelle ei saada muodostettua geometriaa"
                                  :default nil)})
    (catch PSQLException e
      {:ok? false :syy "Odottamaton virhe tieosoitteen validoinnissa"})))

(defn hae-tr-osoite-gps-koordinaateilla [db wgs84-koordinaatit]
  (try
    (let [euref-koordinaatit (geo/wgs84->euref wgs84-koordinaatit)]
      (hae-tr-pisteella db euref-koordinaatit))
    (catch Exception e
      (let [virhe (format "Poikkeus hakiessa tierekisteristeriosoitetta WGS84-koordinaateille %s" wgs84-koordinaatit)]
        (log/error e virhe)
        {:virhe virhe}))))

(defn osavali-olemassa?
  "Palauttaa true, mikäli tien osien välissä on ainakin yksi osa."
  [db tie osa1 osa2]
  (>= (count (tv/tien-osavali db {:tie tie
                                  :osa1 osa1
                                  :osa2 osa2}))
      1))

(defn hae-osien-tiedot
  [db params]
  {:pre (s/valid? ::yllapitokohde/tr-paalupiste params)}
  (map (fn [tieto]
         (update tieto :pituudet konv/jsonb->clojuremap))
       (tv/hae-trpisteiden-valinen-tieto db params)))

(defrecord TierekisteriHaku []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}]
    (julkaise-palvelut
      http-palvelin
      :hae-tr-pisteilla (fn [_ params]
                          (oikeudet/ei-oikeustarkistusta!)
                          (hae-tr-pisteilla db params))

      :hae-tr-pisteella (fn [_ params]
                          (oikeudet/ei-oikeustarkistusta!)
                          (hae-tr-pisteella db params))

      :hae-tr-viivaksi (fn [_ params]
                         (oikeudet/ei-oikeustarkistusta!)
                         (hae-tr-viiva db params))

      :hae-tr-osien-pituudet (fn [_ params]
                               (oikeudet/ei-oikeustarkistusta!)
                               (hae-osien-pituudet db params))
      :hae-tr-pituudet (fn [_ params]
                         (oikeudet/ei-oikeustarkistusta!)
                         (hae-tr-pituudet db params))
      :hae-tr-tiedot (fn [_ params]
                       (oikeudet/ei-oikeustarkistusta!)
                       (hae-osien-tiedot db params))
      :hae-tr-osan-ajoradat (fn [_ params]
                              (oikeudet/ei-oikeustarkistusta!)
                              (hae-tieosan-ajoradat db params))
      :hae-tr-gps-koordinaateilla (fn [_ params]
                                    (oikeudet/ei-oikeustarkistusta!)
                                    (hae-tr-osoite-gps-koordinaateilla db params)))
    this)
  (stop [{http :http-palvelin :as this}]
    (poista-palvelut http
                     :hae-tr-pisteilla
                     :hae-tr-pisteella
                     :hae-tr-viivaksi
                     :hae-osien-pituudet
                     :hae-tr-pituudet
                     :hae-tr-tiedot
                     :hae-tr-osan-ajoradat
                     :hae-tr-gps-koordinaateilla)
    this))
