(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialueet
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.pohjavesialueet :as p]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))


(defn vie-pohjavesialue [db pohjavesialue]
  (if (:the_geom pohjavesialue)
    (let [nimi (:pvnimi pohjavesialue)
          tunnus (:pvnro pohjavesialue)
          geometria (.toString (:the_geom pohjavesialue))
          suolarajoitus true ;; Riippumatta geometria-aineiston pvsuola-arvosta, suolarajoitus voidaan aina antaa
          tr_numero (:tie pohjavesialue)
          tr_alkuosa (:osa pohjavesialue)
          tr_alkuetaisyys (:etaisyys pohjavesialue)
          tr_loppuosa (:losa pohjavesialue)
          tr_loppuetaisyys (:let pohjavesialue)
          tr_ajorata (:ajorata pohjavesialue)
          aineisto_id (:id pohjavesialue)]
      (p/luo-pohjavesialue! db
                            nimi
                            tunnus
                            geometria
                            suolarajoitus
                            tr_numero
                            tr_alkuosa
                            tr_alkuetaisyys
                            tr_loppuosa
                            tr_loppuetaisyys
                            tr_ajorata
                            aineisto_id))
    (log/warn "Pohjavesialuetta ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error pohjavesialue))))

(defn vie-pohjavesialueet-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan pohjavesialueet kantaan tiedostosta " shapefile ". Ei tuoda rivejä, joissa PVNRO puuttuu."))
      (jdbc/with-db-transaction [db db]
        (log/debug "Poistetaan nykyiset pohjavesialueet")
        (p/poista-pohjavesialueet! db)
        (log/debug "Viedään kantaan uudet alueet")
        (doseq [pohjavesialue (filter #(not (= "" (:pvnro %)))(shapefile/tuo shapefile))]
          (vie-pohjavesialue db pohjavesialue)))
      (p/paivita-pohjavesialueet db)
      (p/paivita-pohjavesialue-kooste! db)
      (log/debug "Pohjavesialueiden tuonti kantaan valmis."))
    (log/debug "Pohjavesialueiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
