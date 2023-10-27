(ns harja.palvelin.integraatiot.api.kasittely.tarkastukset
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.tyokalut.sijainnit :as sijainnit]
            [harja.kyselyt.tarkastukset :as q-tarkastukset]
            [harja.kyselyt.tieturvallisuusverkko :as tieturvallisuusverkko-kyselyt]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as tyokalut-liitteet]
            [harja.geo :as geo]))

(defn tallenna-mittaustulokset-tarkastukselle [db id tyyppi uusi? mittaus]
  (case tyyppi
    "talvihoito" (q-tarkastukset/luo-tai-paivita-talvihoitomittaus db id uusi?
                                                                  (-> mittaus
                                                                      (assoc :lampotila-tie (:lampotilaTie mittaus))
                                                                      (assoc :lampotila-ilma (:lampotilaIlma mittaus))))
    "soratie" (q-tarkastukset/luo-tai-paivita-soratiemittaus db id uusi? mittaus)
    nil))

(defn- hae-tieturvallisuusgeometria-tienumerolla [db tie saatugeometria]
  (let [_ (println "LÄHETETÄÄN GEOMETRIA " saatugeometria)
        geometriat (tieturvallisuusverkko-kyselyt/hae-geometriaa-leikkaavat-tieturvallisuusgeometriat-tienumerolla db tie saatugeometria)
        _ (println "Saatiin geometrioita " (count geometriat))
        _ (println "JSONI: " (:jsoni (first geometriat)))]
    (:unioni (first geometriat)))
  )

(defn luo-tai-paivita-tarkastukset
  "Käsittelee annetut tarkastukset ja palautta listan string-varoituksia."
  ([db liitteiden-hallinta kayttaja tyyppi urakka-id data]
   (luo-tai-paivita-tarkastukset db liitteiden-hallinta kayttaja tyyppi urakka-id data nil))
  ([db liitteiden-hallinta kayttaja tyyppi urakka-id data yllapitokohde]
   (let [_ (println "TYYPPI " tyyppi)
         tarkastukset (:tarkastukset data)]
     (remove
       nil?
       (try
         (jdbc/with-db-transaction [db db]
           (mapv
             (fn [rivi]
               (let [tarkastus (:tarkastus rivi)
                     {alkusijainti :alkusijainti loppusijainti :loppusijainti} tarkastus
                     ulkoinen-id (-> tarkastus :tunniste :id)
                     tyyppi (if (nil? tyyppi)
                              (:tarkastustyyppi rivi)
                              (name tyyppi))
                     {tarkastus-id :id}
                     (first
                       (q-tarkastukset/hae-tarkastus-ulkoisella-idlla-ja-tyypilla db ulkoinen-id tyyppi urakka-id))
                     uusi? (nil? tarkastus-id)
                     aika (json/aika-string->java-sql-date (:aika tarkastus))
                     tr-osoite (sijainnit/hae-tierekisteriosoite db (:alkusijainti tarkastus) (:loppusijainti tarkastus))
                     tie (:tie tr-osoite)
                     aosa (:aosa tr-osoite)
                     _ (println "TIE " tie)
                     _ (println "AOSA "aosa)
                     geometria (if tr-osoite
                                 (:geometria tr-osoite)
                                 (sijainnit/tee-geometria (:alkusijainti tarkastus) (:loppusijainti tarkastus)))
                     turvallisuus-geometria (hae-tieturvallisuusgeometria-tienumerolla db tie geometria)
                     _ (println "turvallisuusgeometria " turvallisuus-geometria)
                     id (q-tarkastukset/luo-tai-paivita-tarkastus
                          db kayttaja urakka-id
                          {:id tarkastus-id
                           :lahde "harja-api"
                           :ulkoinen-id ulkoinen-id
                           :tyyppi tyyppi
                           :aika aika
                           :tarkastaja (json/henkilo->nimi (:tarkastaja tarkastus))
                           :sijainti geometria
                           :tr {:numero (:tie tr-osoite)
                                :alkuosa (:aosa tr-osoite)
                                :alkuetaisyys (:aet tr-osoite)
                                :loppuosa (:losa tr-osoite)
                                :loppuetaisyys (:let tr-osoite)}
                           :havainnot (:havainnot tarkastus)
                           :yllapitokohde yllapitokohde
                           :laadunalitus (let [alitus (:laadunalitus tarkastus)]
                                           (if (nil? alitus)
                                             (and
                                               (nil? yllapitokohde)
                                               (not (str/blank? (:havainnot tarkastus))))
                                             alitus))
                           :nayta-urakoitsijalle (boolean (:naytetaan-urakoitsijalle tarkastus))
                           :pisteet
                           (->
                             (if-not loppusijainti
                               {:type :point :coordinates [(:x alkusijainti) (:y alkusijainti)]}
                               {:type :multipoint,
                                :coordinates [{:type :point :coordinates [(:x alkusijainti) (:y alkusijainti)]}
                                              {:type :point, :coordinates [(:x loppusijainti) (:y loppusijainti)]}]})
                             geo/clj->pg
                             geo/geometry)})
                     liitteet (:liitteet tarkastus)]
                 (tyokalut-liitteet/tallenna-liitteet-tarkastukselle db liitteiden-hallinta urakka-id id kayttaja liitteet)
                 (tallenna-mittaustulokset-tarkastukselle db id tyyppi uusi? (:mittaus rivi))
                 (when-not tr-osoite
                   (format "Annetulla sijainnilla ei voitu päätellä sijaintia tieverkolla (alku: %s, loppu %s)."
                           (:alkusijainti tarkastus) (:loppusijainti tarkastus)))))
             tarkastukset))
         (catch Throwable t
           (log/warn t "Virhe tarkastuksen lisäämisessä")
           (throw t)))))))
