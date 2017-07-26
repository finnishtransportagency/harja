(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.sillat
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.siltatarkastukset :as q]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))


(defn luo-tai-paivita-silta [db {:keys [siltaty siltanimi siltanro silta_id the_geom
                                        tie aosa aet
                                        akspaino telipaino ajonpaino
                                        ]}]
  (let [id (int silta_id)
        nro (int siltanro)
        params {;; Sillan osoite
                :numero tie :aosa aosa :aet aet

                ;; Sillan tunnisteet ja nimi
                :siltaid id
                :siltanro nro
                :nimi siltanimi

                ;; Tyyppi. HUOM: Ennen tekstimuotoinen, mutta tyypit tierekisterissä ei samat
                :tyyppi (str (int siltaty))

                :geometria (str the_geom)

                ;; Painorajoitukset
                :akselipaino akspaino
                :telipaino telipaino
                :ajoneuvopaino ajonpaino

                ;; Tunnusta ei tierekisterissä ole
                :tunnus nil}]

    (if (first (q/hae-silta-idlla db id))
      (q/paivita-silta-idlla! db params)
      (q/luo-silta! db params))))

(defn vie-silta-entry [db silta]
  (if (:the_geom silta)
    (luo-tai-paivita-silta db silta)
    (log/warn "Siltaa ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error silta))))

(defn vie-sillat-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan sillat kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db]
        (doseq [silta (shapefile/tuo shapefile)]
          (vie-silta-entry db silta)))
      (s/paivita-urakoiden-sillat db)
      (log/debug "Siltojen tuonti kantaan valmis."))
    (log/debug "Siltojen tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
