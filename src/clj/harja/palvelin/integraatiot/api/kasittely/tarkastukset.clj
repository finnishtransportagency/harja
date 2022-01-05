(ns harja.palvelin.integraatiot.api.kasittely.tarkastukset
  (:require [taoensso.timbre :as log]
            [clojure.java.jdbc :as jdbc]
            [clojure.string :as str]
            [harja.palvelin.integraatiot.api.tyokalut.sijainnit :as sijainnit]
            [harja.kyselyt.tarkastukset :as q-tarkastukset]
            [harja.palvelin.integraatiot.api.tyokalut.json :as json]
            [harja.palvelin.integraatiot.api.tyokalut.liitteet :as tyokalut-liitteet]
            [harja.kyselyt.liitteet :as liitteet-q]))


(defn tallenna-vain-uudet-liitteet
  "Tarkastusten päivitysten mukana voi tulla sama liite uudelleen. Tarkistetaan, että onko liite jo otettu huomioon.
  Samalla kuitenkin tallennetaan mahdolliset uudet liitteet."
  [db urakka-id tarkastus-id liitteet liitteiden-hallinta kayttaja]
  (let [palauta-jos-uniikki (fn [l]
                              (let [liite (:liite l)
                                    nimi (:nimi liite)
                                    data (tyokalut-liitteet/dekoodaa-base64 (:sisalto liite))
                                    koko (alength data)
                                    db-liite (liitteet-q/hae-liite-meta-tiedoilla db
                                               {:urakka-id urakka-id :nimi nimi :koko koko})]
                                (if (or (nil? db-liite) (empty? db-liite))
                                  l
                                  nil)))
        uniikit-liitteet (keep #(palauta-jos-uniikki %) liitteet)]
    (tyokalut-liitteet/tallenna-liitteet-tarkastukselle db liitteiden-hallinta urakka-id tarkastus-id kayttaja uniikit-liitteet)))

(defn tallenna-mittaustulokset-tarkastukselle [db id tyyppi uusi? mittaus]
  (case tyyppi
    "talvihoito" (q-tarkastukset/luo-tai-paivita-talvihoitomittaus db id uusi?
                                                                  (-> mittaus
                                                                      (assoc :lampotila-tie (:lampotilaTie mittaus))
                                                                      (assoc :lampotila-ilma (:lampotilaIlma mittaus))))
    "soratie" (q-tarkastukset/luo-tai-paivita-soratiemittaus db id uusi? mittaus)
    nil))

(defn luo-tai-paivita-tarkastukset
  "Käsittelee annetut tarkastukset ja palautta listan string-varoituksia."
  ([db liitteiden-hallinta kayttaja tyyppi urakka-id data]
   (luo-tai-paivita-tarkastukset db liitteiden-hallinta kayttaja tyyppi urakka-id data nil))
  ([db liitteiden-hallinta kayttaja tyyppi urakka-id data yllapitokohde]
   (let [tarkastukset (:tarkastukset data)]
     (remove
       nil?
       (try
         (jdbc/with-db-transaction [db db]
           (mapv
             (fn [rivi]
               (let [tarkastus (:tarkastus rivi)
                     ulkoinen-id (-> tarkastus :tunniste :id)
                     tyyppi (if (nil? tyyppi)
                              (:tarkastustyyppi rivi)
                              (name tyyppi))
                     {tarkastus-id :id}
                     (first
                       (q-tarkastukset/hae-tarkastus-ulkoisella-idlla-ja-tyypilla db ulkoinen-id tyyppi (:id kayttaja)))
                     uusi? (nil? tarkastus-id)
                     aika (json/aika-string->java-sql-date (:aika tarkastus))
                     tr-osoite (sijainnit/hae-tierekisteriosoite db (:alkusijainti tarkastus) (:loppusijainti tarkastus))
                     geometria (if tr-osoite
                                 (:geometria tr-osoite)
                                 (sijainnit/tee-geometria (:alkusijainti tarkastus) (:loppusijainti tarkastus)))
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
                           :nayta-urakoitsijalle (boolean (:naytetaan-urakoitsijalle tarkastus))})
                     liitteet (:liitteet tarkastus)]
                 ;; Muokattaessa tarkastusta tarkistetaan onko liite jo annettu. Jos on, niin ei lisätä uutta
                 (if uusi?
                   (tyokalut-liitteet/tallenna-liitteet-tarkastukselle db liitteiden-hallinta urakka-id id kayttaja liitteet)
                   (tallenna-vain-uudet-liitteet db urakka-id tarkastus-id liitteet liitteiden-hallinta kayttaja))
                 (tallenna-mittaustulokset-tarkastukselle db id tyyppi uusi? (:mittaus rivi))
                 (when-not tr-osoite
                   (format "Annetulla sijainnilla ei voitu päätellä sijaintia tieverkolla (alku: %s, loppu %s)."
                           (:alkusijainti tarkastus) (:loppusijainti tarkastus)))))
             tarkastukset))
         (catch Throwable t
           (log/warn t "Virhe tarkastuksen lisäämisessä")
           (throw t)))))))
