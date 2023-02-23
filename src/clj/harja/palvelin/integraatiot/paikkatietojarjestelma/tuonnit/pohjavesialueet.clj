(ns harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.pohjavesialueet
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.pohjavesialueet :as p]
            [harja.palvelin.integraatiot.paikkatietojarjestelma.tuonnit.shapefile :as shapefile]
            [clojure.string :as str]))

;; Pohjavesialueen shapefilen kenttien nimet tulevat velhosta, ja shapefilen rajoituksista johtuen kenttien nimet
;; rajataan 10 merkkiin.
;; harja-avain      | Velho-kenttä                          |   shapefile-kenttä
;; ---------------  |---------------------------------------|---------------------
;; nimi             | rakenteelliset_ominaisuudet_nimi      | rakenteell
;; tunnus           | toiminnalliset_ominaisuudet_tunnus    | toiminnall
;; tr_numero        | alkusijainti_tie / loppusijainti_tie  | alkusijain
;; tr_alkuosa       | alkusijainti_osa                      | alkusijai0
;; tr_alkuetaisyys  | alkusijainti_etaisyys                 | alkusijai1
;; tr_loppuosa      | loppusijainti_osa                     | loppusija0
;; tr_loppuetaisyys | loppusijainti_etaisyys                | loppusija1
;; tr_ajorata       | <Ei saatavilla                        | <Ei saatavilla>
;; aineisto-id      | internal_id                           | internal_i
(defn vie-pohjavesialue [db pohjavesialue]
  (if (:the_geom pohjavesialue)
    (let [nimi (:rakenteell pohjavesialue)
          tunnus (str/replace (:toiminnall pohjavesialue) #"^0+(?!$)" "") ;; Poistetaan nollat alusta, aineistossa alun nollien määrä joskus vaihtelee
          geometria (.toString (:the_geom pohjavesialue))
          suolarajoitus true ;; Riippumatta geometria-aineiston pvsuola-arvosta, suolarajoitus voidaan aina antaa
          tr_numero (:alkusijain pohjavesialue)
          tr_alkuosa (:alkusijai0 pohjavesialue)
          tr_alkuetaisyys (:alkusijai1 pohjavesialue)
          tr_loppuosa (:loppusija0 pohjavesialue)
          tr_loppuetaisyys (:loppusija1 pohjavesialue)
          tr_ajorata nil
          aineisto_id (:internal_i pohjavesialue)]
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
      (log/debug (str "Tuodaan pohjavesialueet kantaan tiedostosta " shapefile ". Ei tuoda rivejä, joissa tunnus puuttuu."))
      (jdbc/with-db-transaction [db db]
        (log/debug "Poistetaan nykyiset pohjavesialueet")
        (p/poista-pohjavesialueet! db)
        (log/debug "Viedään kantaan uudet alueet")
        (doseq [pohjavesialue (filter #(not (= "" (:toiminnall %))) (shapefile/tuo shapefile))]
          (vie-pohjavesialue db pohjavesialue)))
      (p/paivita-pohjavesialueet db)
      (p/paivita-pohjavesialue-kooste db)
      (log/debug "Pohjavesialueiden tuonti kantaan valmis."))
    (log/debug "Pohjavesialueiden tiedostoa ei löydy konfiguraatiosta. Tuontia ei suoriteta.")))
