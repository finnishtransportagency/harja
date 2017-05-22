(ns harja.palvelin.palvelut.tierek-haku
  (:require [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelut poista-palvelut]]
            [com.stuartsierra.component :as component]
            [harja.kyselyt.tieverkko :as tv]
            [harja.geo :as geo]
            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.tierekisteri :as tr-domain])
  (:import (org.postgresql.util PSQLException)))

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
       ajorata-olemassa? (fn [osa]
                           (or (not ajorata)
                               (some #(and (= osa (:osa %)) (= ajorata (:ajorata %)))
                                     ajoratojen-pituudet)))
       tulos {:aosa-olemassa? (tr-domain/osa-olemassa-verkolla? aosa osien-pituudet)
              :losa-olemassa? (tr-domain/osa-olemassa-verkolla? losa osien-pituudet)
              :alkuosan-ajorata-olemassa? (ajorata-olemassa? aosa)
              :loppuosan-ajorata-olemassa? (ajorata-olemassa? losa)
              :aosa-pituus-validi? (if ajorata
                                     (tr-domain/ajoradan-pituus-sopiva-verkolla? aosa ajorata aet ajoratojen-pituudet)
                                     (tr-domain/osan-pituus-sopiva-verkolla? aosa aet osien-pituudet))
              :losa-pituus-validi? (if
                                     (tr-domain/ajoradan-pituus-sopiva-verkolla? losa ajorata let ajoratojen-pituudet)
                                     (tr-domain/osan-pituus-sopiva-verkolla? losa let osien-pituudet))
              :geometria-validi? (some? (hae-tr-viiva db {:numero tienumero
                                                          :alkuosa aosa
                                                          :alkuetaisyys aet
                                                          :loppuosa losa
                                                          :loppuetaisyys let}))}
       kaikki-ok? (every? true? (vals tulos))]
      {:ok? kaikki-ok? :syy (cond (not (:aosa-olemassa? tulos)) (str "Alkuosaa " aosa " ei ole olemassa")
                                  (not (:losa-olemassa? tulos)) (str "Loppuosaa " losa " ei ole olemassa")
                                  (not (:alkuosan-ajorata-olemassa? tulos)) (str "Alkuosan" aosa " ajorataa ei ole olemassa")
                                  (not (:loppuosan-ajorata-olemassa? tulos)) (str "Loppuosan" losa " ajorataa ei ole olemassa")
                                  (not (:aosa-pituus-validi? tulos)) (str "Alkuosan pituus " aet " ei kelpaa")
                                  (not (:losa-pituus-validi? tulos)) (str "Loppuosan pituus " let " ei kelpaa")
                                  (not (:geometria-validi? tulos)) "Osoitteelle ei saada muodostettua geometriaa"
                                  :default nil)})
    (catch PSQLException e
      {:ok? false :syy "Odottamaton virhe tieosoitteen validoinnissa"})))

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

      :hae-tr-osan-ajoradat (fn [_ params]
                              (oikeudet/ei-oikeustarkistusta!)
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
