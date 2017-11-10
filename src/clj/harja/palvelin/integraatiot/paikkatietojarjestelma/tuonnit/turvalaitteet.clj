(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.turvalaitteet
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.vesivaylat.turvalaitteet :as turvalaitteet]

            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]))


;; maaritlaine pitäiskö tää tiedosto nimetä vaan turvalaitteet ja pitäiskö tää laittaa omaan vesiväylät-kansioon?

;; maaritlaine ??????
;; jos väylät tulee näin: 2344, 3423, 43234 pitääkö ne käsitellä jotenkin ennen ku ne nakataan tonne?
;; mikä sen väylät-kentän tyyppi pitäis olla.
(defn vie-turvalaite-entry [db {:keys [the_geom siltapalve] :as turvalaite}]
  (if the_geom
    (turvalaitteet/vie-turvalaitetauluun<! db turvalaite (str the_geom))
    (log/warn "Siltapalvelusopimusta ei voida tuoda ilman geometriaa. Virheviesti: " (:loc_error urakka))))

(defn vie-vesivaylien-turvalaitteet-kantaan [db shapefile]
  (if shapefile
    (do
      (log/debug (str "Tuodaan siltapalvelusopimukset kantaan tiedostosta " shapefile))
      (jdbc/with-db-transaction [db db] ;;maaritlaine mitä noi parametrit binding ja bodyt on?
                                (doseq [turvalaite (shapefile/tuo shapefile)]
                                  (vie-turvalaite-entry db turvalaite))
                                (log/debug "Vesiväylien turvalaitteiden tuonti kantaan valmis.")))
    (log/debug "Vesiväylien turvalaitteiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
