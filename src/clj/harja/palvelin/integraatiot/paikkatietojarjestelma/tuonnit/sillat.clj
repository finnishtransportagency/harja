(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.siltatarkastukset :as s]
            [harja.tyokalut.functor :as functor]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))

(defn float-intiksi [v]
  (if (float? v)
    (int v)
    v))

(defn mapin-floatit-inteiksi [m]
  (into {} (map (fn [[k v]]
                  [k (float-intiksi v)])
                m)))

(defn luo-tai-paivita-silta [db silta-floateilla]
  (let [silta (mapin-floatit-inteiksi silta-floateilla)
        tyyppi (:rakennety silta)
        numero (:siltanro silta)
        nimi (:siltanimi silta)
        geometria (.toString (:the_geom silta))
        tie (:tie silta)
        alkuosa (:aosa silta)
        alkuetaisyys (:aet silta)
        ely-lyhenne (:ely_lyhenne silta)
        tunnus (when (not-empty ely-lyhenne)
                 (str ely-lyhenne "-" numero))
        id (when :silta_id silta
                 (int (:silta_id silta)))]
    #_(log/debug "silta luettu tl261-tietueesta:" tyyppi, numero, nimi, tie, alkuosa, alkuetaisyys, tunnus, id)
    (when (and id tunnus)
      (if (first (s/hae-silta-idlla db id))
        (s/paivita-silta-idlla! db tyyppi numero nimi geometria tie alkuosa alkuetaisyys tunnus id)
        (s/luo-silta! db tyyppi numero nimi geometria tie alkuosa alkuetaisyys tunnus id)))))

(defn vie-silta-entry [db silta]
  (let [pakolliset-avaimet [:the_geom :ely_lyhenne :silta_id]]
    (if (every? some? (mapv silta pakolliset-avaimet))
      (luo-tai-paivita-silta db silta)
      #_(log/debug "Siltaa ei voida tuoda ilman geometriaa, ely-lyhennettä ja silta-id:tä. Virheviesti: " (:loc_error silta) "Silta-map:" silta))))

(defn vie-sillat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan sillat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (doseq [silta (take 10 (shapefile/tuo shapefile))]
          (vie-silta-entry db silta)))
      (s/paivita-urakoiden-sillat db)
      (log/debug "Siltojen tuonti kantaan valmis."))
    (log/debug "Siltojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
