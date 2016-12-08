(ns harja-laadunseuranta.tarkastukset
  (:require [taoensso.timbre :as log]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [harja-laadunseuranta.utils :as utils]
            [harja.kyselyt.tarkastukset :as tark-q]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

(def db tietokanta/db)

(defn etenemissuunta
  "Palauttaa 1 jos tr-osoite2 on suurempi kuin tr-osoite1.
   Palauttaa -1 jos tr-osoite2 on pienempi kuin tr-osoite1
   Palauttaa 0 jos samat.
   Jos ei jostain syystä voida määrittää, palauttaa nil"
  [tr-osoite1 tr-osoite2]
  (when (and (:aet tr-osoite1) (:aet tr-osoite2))
    (cond
      (< (:aet tr-osoite1) (:aet tr-osoite2)) -1
      (> (:aet tr-osoite1) (:aet tr-osoite2)) 1
      (= (:aet tr-osoite1) (:aet tr-osoite2)) 0)))

;; NOTE On mahdollista, että epätarkka GPS heittääkin yhden pisteen muita taaemmas, jolloin tilanne
;; tulkitaan ympärikääntymiseksi.
(defn tr-osoitteet-sisaltavat-ymparikaantymisen?
  "Ottaa kolme tr-osoitetta vectorissa ja kertoo sisältävätkö ne ympärikääntymisen. Päättely tehdään niin, että
   tutkitaan ensin mihin suuntaan edetään kahden ensimmäisen pisteen kohdalla ja jos kolmas piste eteneekin
   päinvastaiseen suuntaan, on tapahtunut ympärikääntyminen."
  [tr-osoitteet]
  (if (every? some? tr-osoitteet)
    (let [etenemissuunta-piste1-piste2 (etenemissuunta (first tr-osoitteet) (second tr-osoitteet))
          etenemissuunta-piste2-piste3 (etenemissuunta (second tr-osoitteet) (get tr-osoitteet 2))]
      (if (or (= etenemissuunta-piste1-piste2 0)
              (= etenemissuunta-piste2-piste3 0))
        false
        (not= etenemissuunta-piste1-piste2 etenemissuunta-piste2-piste3)))
    false))

(def +kahden-pisteen-valinen-sallittu-aikaero-s+ 180)

(defn- tarkastus-jatkuu?
  "Ottaa reittimerkinnän ja järjestyksesä seuraavan reittimerkinnän ja kertoo muodostavatko ne loogisen jatkumon,
   toisin sanoen tulkitaanko seuraavan pisteen olevan osa samaa tarkastusta vai ei."
  [nykyinen-reittimerkinta seuraava-reittimerkinta]
  (and
    ;; Jatkuvat havainnot pysyvät samana myös seuraavassa pisteessä
    (= (:jatkuvat-havainnot nykyinen-reittimerkinta) (:jatkuvat-havainnot seuraava-reittimerkinta))
    ;; Seuraava piste on osa samaa tietä. Jos seuraavalle pistelle ei ole pystytty määrittelemään tietä,
    ;; niin oletetaan kuitenkin, että se on osa samaa tarkastusta niin kauan kuin osoite oikeasti vaihtuu
    (or (nil? (:tr-osoite seuraava-reittimerkinta))
        (= (get-in nykyinen-reittimerkinta [:tr-osoite :tie]) (get-in seuraava-reittimerkinta [:tr-osoite :tie])))
    ;; Edellisen pisteen kirjauksesta ei ole kulunut ajallisesti liian kauan
    ;; Jos on kulunut, emme tiedä, mitä näiden pisteiden välillä on tapahtunut, joten on turvallista
    ;; päättää edellinen tarkastus ja aloittaa uusi.
    (or
      (nil? (:aikaleima nykyinen-reittimerkinta))
      (nil? (:aikaleima seuraava-reittimerkinta))
      (<= (t/in-seconds (t/interval (c/from-sql-time (:aikaleima nykyinen-reittimerkinta))
                                    (c/from-sql-time (:aikaleima seuraava-reittimerkinta))))
          +kahden-pisteen-valinen-sallittu-aikaero-s+))

    ;; Seuraava piste ei aiheuta reitin kääntymistä ympäri
    ;; PENDING GPS:n epätarkkuudesta johtuen aiheuttaa liikaa ympärikääntymisiä eikä toimi oikein, siksi kommentoitu
    #_(not (tr-osoitteet-sisaltavat-ymparikaantymisen? [; Edellinen sijainti
                                                        (:tr-osoite (get (:sijainnit nykyinen-reittimerkinta) (- (count (:sijainnit nykyinen-reittimerkinta)) 2)))
                                                        ;; Nykyinen sijainti
                                                        (:tr-osoite (last (:sijainnit nykyinen-reittimerkinta)))
                                                        ;; Seuraavan pisteen sijainti
                                                        (:tr-osoite seuraava-reittimerkinta)]))))

(defn- yhdista-reittimerkinnan-kaikki-havainnot
  "Yhdistää reittimerkinnän pistemäiset havainnot ja jatkuvat havainnot."
  [reittimerkinta]
  (if (empty? (:jatkuvat-havainnot reittimerkinta))
    (filterv some? [(:pistemainen-havainto reittimerkinta)])
    (filterv some? (conj (:jatkuvat-havainnot reittimerkinta)
                         (:pistemainen-havainto reittimerkinta)))))

(defn keskiarvo
  [numerot]
  (cond
    (nil? numerot)
    nil

    (number? numerot)
    numerot

    (empty? numerot)
    nil

    :default
    (float (with-precision 3
             (/ (apply + numerot) (count numerot))))))

(defn- paattele-tarkastustyyppi [reittimerkinta]
  (cond
    ;; Jos sisältää soratiemittauksia, tyyppi on soratiemittaus
    (or (:soratie-tasaisuus reittimerkinta)
        (:kiinteys reittimerkinta)
        (:polyavyys reittimerkinta)
        (:sivukaltevuus reittimerkinta))
    "soratie"

    ;; Muuten laatutarkastus
    :default
    "laatu"))

(defn- reittimerkinta-tarkastukseksi
  "Muuntaa reittimerkinnän Harja-tarkastukseksi.
   Reittimerkintä voi olla joko yksittäinen (pistemäinen) reittimerkintä tai
   jatkuvista havainnoista kasattu, yhdistetty reittimerkintä."
  [reittimerkinta]
  (let [yhdista-mittausarvot (fn [reittimerkinta mittaus-avain]
                               (cond
                                 (nil? (mittaus-avain reittimerkinta))
                                 nil

                                 (number? (mittaus-avain reittimerkinta))
                                 (mittaus-avain reittimerkinta)

                                 (vector? (mittaus-avain reittimerkinta))
                                 (keskiarvo (mittaus-avain reittimerkinta))))]
    {:aika (:aikaleima reittimerkinta)
     :tyyppi (paattele-tarkastustyyppi reittimerkinta)
     :tarkastusajo (:tarkastusajo reittimerkinta)
     :sijainnit (or (:sijainnit reittimerkinta) [{:sijainti (:sijainti reittimerkinta)
                                                  :tr-osoite (:tr-osoite reittimerkinta)}])
     :liite (:kuva reittimerkinta) ;; Liitteen id tai vector jos monta
     :vakiohavainnot (yhdista-reittimerkinnan-kaikki-havainnot reittimerkinta)
     :havainnot (:kuvaus reittimerkinta)
     :talvihoitomittaus {:talvihoitoluokka nil
                         :lumimaara (yhdista-mittausarvot reittimerkinta :lumisuus)
                         :tasaisuus (yhdista-mittausarvot reittimerkinta :talvihoito-tasaisuus)
                         :kitka (yhdista-mittausarvot reittimerkinta :kitkamittaus)
                         :ajosuunta nil
                         :lampotila_ilma (yhdista-mittausarvot reittimerkinta :lampotila)
                         :lampotila_tie nil}
     :soratiemittaus {:hoitoluokka nil
                      :tasaisuus (yhdista-mittausarvot reittimerkinta :soratie-tasaisuus)
                      :kiinteys (yhdista-mittausarvot reittimerkinta :kiinteys)
                      :polyavyys (yhdista-mittausarvot reittimerkinta :polyavyys)
                      :sivukaltevuus (yhdista-mittausarvot reittimerkinta :sivukaltevuus)}
     :laadunalitus (or (boolean (:laadunalitus reittimerkinta)) false)}))

(defn viimeinen-indeksi [sekvenssi]
  (- (count sekvenssi) 1))

(defn- keraa-seuraavan-pisteen-mittaus
  "Ottaa reittimerkinnän ja järjestyksessä seuraavan reittimerkinnän.
   Lisää seuraavan mittauksen tiedot edelliseen."
  [reittimerkinta seuraava-reittimerkinta mittaus-avain]
  (if (nil? (mittaus-avain seuraava-reittimerkinta))
    reittimerkinta

    (cond (nil? (mittaus-avain reittimerkinta)) ;; Aseta arvoksi seuraava mittaus
          (assoc reittimerkinta mittaus-avain (mittaus-avain seuraava-reittimerkinta))

          (number? (mittaus-avain reittimerkinta)) ;; Muunna vectoriksi
          (assoc reittimerkinta mittaus-avain [(mittaus-avain reittimerkinta)
                                               (mittaus-avain seuraava-reittimerkinta)])

          (vector? (mittaus-avain reittimerkinta)) ;; Lisää seuraava arvo vectoriin
          (assoc reittimerkinta mittaus-avain (conj (mittaus-avain reittimerkinta)
                                                    (mittaus-avain seuraava-reittimerkinta))))))

(defn- keraa-seuraavan-pisteen-sijainti
  "Ottaa reittimerkinnän ja järjestyksessä seuraavan reittimerkinnän.
   Lisää seuraavan sijainnin ja TR-osoitteen tiedot edelliseen."
  [reittimerkinta seuraava-reittimerkinta]
  (if (nil? (:sijainti seuraava-reittimerkinta)) ; Käytännössä mahdoton tilanne, mutta tarkistetaan nyt kuitenkin
    reittimerkinta
    (if (nil? (:sijainnit reittimerkinta))
      (-> reittimerkinta
          (assoc :sijainnit [{:sijainti (:sijainti reittimerkinta)
                              :tr-osoite (:tr-osoite reittimerkinta)}
                             {:sijainti (:sijainti seuraava-reittimerkinta)
                              :tr-osoite (:tr-osoite seuraava-reittimerkinta)}])
          (dissoc :sijainti))
      (assoc reittimerkinta :sijainnit (conj (:sijainnit reittimerkinta) {:sijainti (:sijainti seuraava-reittimerkinta)
                                                                          :tr-osoite (:tr-osoite seuraava-reittimerkinta)})))))

(defn- keraa-seuraavan-pisteen-laadunalitus
  "Ottaa reittimerkinnän ja järjestyksessä seuraavan reittimerkinnän.
   Asettaa laadunalituksen trueksi, jos seuraavalla merkinnällä on laadunalitus."
  [reittimerkinta seuraava-reittimerkinta]
  (if (true? (:laadunalitus seuraava-reittimerkinta))
    (assoc reittimerkinta :laadunalitus true)
    reittimerkinta))

(defn- keraa-reittimerkintojen-kuvaukset
  "Yhdistää samalla jatkuvalla havainnolla olevat kuvauskentät yhteen"
  [reittimerkinta seuraava-reittimerkinta]
  (if (nil? (:kuvaus seuraava-reittimerkinta))
    reittimerkinta
    (assoc reittimerkinta :kuvaus (str (when-let [k (:kuvaus reittimerkinta)]
                                         (str k "\n")) (:kuvaus seuraava-reittimerkinta)))))

(defn- yhdista-jatkuvat-reittimerkinnat
  "Ottaa joukon reittimerkintöjä ja yhdistää ne yhdeksi loogiseksi jatkumoksi."
  [reittimerkinnat]
  (reduce
    (fn [reittimerkinnat seuraava-merkinta]
      (if (empty? reittimerkinnat)
        (conj reittimerkinnat seuraava-merkinta)
        (let [viimeisin-yhdistetty-reittimerkinta (last reittimerkinnat)]
          (if (tarkastus-jatkuu? viimeisin-yhdistetty-reittimerkinta seuraava-merkinta)
            ;; Sama tarkastus jatkuu, ota seuraavan mittauksen tiedot
            ;; ja lisää ne viimeisimpään reittimerkintään
            (assoc reittimerkinnat
              (viimeinen-indeksi reittimerkinnat)
              (-> viimeisin-yhdistetty-reittimerkinta
                  (keraa-seuraavan-pisteen-sijainti seuraava-merkinta)
                  (keraa-seuraavan-pisteen-laadunalitus seuraava-merkinta)
                  (keraa-seuraavan-pisteen-mittaus seuraava-merkinta :talvihoito-tasaisuus)
                  (keraa-seuraavan-pisteen-mittaus seuraava-merkinta :lumisuus)
                  (keraa-seuraavan-pisteen-mittaus seuraava-merkinta :kitkamittaus)
                  (keraa-seuraavan-pisteen-mittaus seuraava-merkinta :soratie-tasaisuus)
                  (keraa-seuraavan-pisteen-mittaus seuraava-merkinta :kiinteys)
                  (keraa-seuraavan-pisteen-mittaus seuraava-merkinta :polyavyys)
                  (keraa-seuraavan-pisteen-mittaus seuraava-merkinta :sivukaltevuus)
                  (keraa-seuraavan-pisteen-mittaus seuraava-merkinta :lampotila)
                  ;; Okei, kuva ei ole mittaus, mutta kerääminen toimii samalla logiikalla :-)
                  (keraa-seuraavan-pisteen-mittaus seuraava-merkinta :kuva)
                  (keraa-reittimerkintojen-kuvaukset seuraava-merkinta)))
            ;; Uusi tarkastus alkaa
            (conj reittimerkinnat seuraava-merkinta)))))
    []
    reittimerkinnat))

(defn- pistemainen-havainto?
  "Onko havainto pistemäinen?"
  [reittimerkinta]
  (or (:pistemainen-havainto reittimerkinta)
      (and (:kuva reittimerkinta) (empty? (:jatkuvat-havainnot reittimerkinta)))))

(defn- reittimerkinnat-reitillisiksi-tarkastuksiksi
  "Käy annetut reittimerkinnät läpi ja muodostaa niistä reitilliset tarkastukset"
  [reittimerkinnat]
  (let [jatkuvat-reittimerkinnat (filter (comp not pistemainen-havainto?) reittimerkinnat)
        yhdistetyt-reittimerkinnat (yhdista-jatkuvat-reittimerkinnat jatkuvat-reittimerkinnat)]
    (mapv reittimerkinta-tarkastukseksi yhdistetyt-reittimerkinnat)))

(defn- reittimerkinnat-pistemaisiksi-tarkastuksiksi
  "Käy annetut reittimerkinnät läpi ja muodostaa niistä pistemäiset tarkastukset"
  [reittimerkinnat]
  (let [pistemaiset-reittimerkinnat (filter pistemainen-havainto? reittimerkinnat)]
    (mapv reittimerkinta-tarkastukseksi pistemaiset-reittimerkinnat)))

(defn reittimerkinnat-tarkastuksiksi
  "Käy reittimerkinnät läpi ja palauttaa mapin, jossa reittimerkinnät muutettu
   reitillisiksi ja pistemäisiksi tarkastuksiksi"
  [tr-osoitteelliset-reittimerkinnat]
  {:reitilliset-tarkastukset (reittimerkinnat-reitillisiksi-tarkastuksiksi tr-osoitteelliset-reittimerkinnat)
   :pistemaiset-tarkastukset (reittimerkinnat-pistemaisiksi-tarkastuksiksi tr-osoitteelliset-reittimerkinnat)})

(defn luo-tallennettava-tarkastus [tarkastus kayttaja]
  (let [tarkastuksen-reitti (:sijainnit tarkastus)
        lahtopiste (:tr-osoite (first tarkastuksen-reitti))
        paatepiste (:tr-osoite (last tarkastuksen-reitti))
        koko-tarkastuksen-tr-osoite {:tie (:tie lahtopiste)
                                     :aosa (:aosa lahtopiste)
                                     :aet (:aet lahtopiste)
                                     ;; Loppuosa on pistemäisessä osoitteessa sama kuin alku,
                                     ;; muuten normaali loppuosa
                                     :losa (or (:losa paatepiste) (:aosa paatepiste))
                                     :let (or (:let paatepiste) (:aet paatepiste))}]
    (assoc tarkastus
      :tarkastaja (str (:etunimi kayttaja) " " (:sukunimi kayttaja))
      :tr_numero (:tie koko-tarkastuksen-tr-osoite)
      :tr_alkuosa (:aosa koko-tarkastuksen-tr-osoite)
      :tr_alkuetaisyys (:aet koko-tarkastuksen-tr-osoite)
      :tr_loppuosa (:losa koko-tarkastuksen-tr-osoite)
      :tr_loppuetaisyys (:let koko-tarkastuksen-tr-osoite)
      :lahde "harja-ls-mobiili")))

(defn hae-tallennettavan-tarkastuksen-sijainti
  [db {tie :tr_numero
       alkuosa :tr_alkuosa alkuet :tr_alkuetaisyys
       loppuosa :tr_loppuosa loppuet :tr_loppuetaisyys}]
  (when (and tie alkuosa alkuet)
    (let [viiva? (and loppuosa loppuet)]
      (:geom
        (first (q/tr-osoitteelle-viiva
                 db
                 {:tr_numero tie
                  :tr_alkuosa alkuosa
                  :tr_alkuetaisyys alkuet
                  :tr_loppuosa (if viiva? loppuosa alkuosa)
                  :tr_loppuetaisyys (if viiva? loppuet alkuet)}))))))

(defn- tallenna-tarkastus! [db tarkastus kayttaja]
  (log/debug "Aloitetaan tarkastuksen tallennus")
  (let [tarkastus (luo-tallennettava-tarkastus tarkastus kayttaja)
        geometria (hae-tallennettavan-tarkastuksen-sijainti db tarkastus)
        tarkastus (as-> tarkastus tarkastus
                        (assoc tarkastus :sijainti geometria))
        _ (q/luo-uusi-tarkastus<! db
                                  (merge tarkastus
                                         {:luoja (:id kayttaja)}))
        _ (log/debug "Uusi tarkastus luotu!")
        tarkastus-id (tark-q/luodun-tarkastuksen-id db)
        sisaltaa-talvihoitomittauksen? (not (empty? (remove nil? (vals (:talvihoitomittaus tarkastus)))))
        sisaltaa-soratiemittauksen? (not (empty? (remove nil? (vals (:soratiemittaus tarkastus)))))]


    (doseq [vakiohavainto-id (:vakiohavainnot tarkastus)]
      (log/debug "Tallennetaan vakiohavainnot: " (pr-str (:vakiohavainnot tarkastus)))
      (q/luo-uusi-tarkastuksen-vakiohavainto<! db
                                               {:tarkastus tarkastus-id
                                                :vakiohavainto vakiohavainto-id}))
    (when sisaltaa-talvihoitomittauksen?
      (log/debug "Tallennetaan talvihoitomittaus: " (pr-str (:talvihoitomittaus tarkastus)))
      (q/luo-uusi-talvihoitomittaus<! db
                                      (merge (:talvihoitomittaus tarkastus)
                                             {:tarkastus tarkastus-id})))
    (when sisaltaa-soratiemittauksen?
      (log/debug "Tallennetaan soratiemittaus: " (pr-str (:soratiemittaus tarkastus)))
      (q/luo-uusi-soratiemittaus<! db
                                   (merge (:soratiemittaus tarkastus)
                                          {:tarkastus tarkastus-id})))
    (cond (number? (:liite tarkastus))
          (do
            (log/debug "Tallennetaan liite: " (pr-str (:liite tarkastus)))
            (q/luo-uusi-tarkastus-liite<! db
                                          {:tarkastus tarkastus-id
                                           :liite (:liite tarkastus)}))

          (vector? (:liite tarkastus))
          (doseq [liite (:liite tarkastus)]
            (log/debug "Tallennetaan liite (yksi monesta): " (pr-str liite))
            (q/luo-uusi-tarkastus-liite<! db
                                          {:tarkastus tarkastus-id
                                           :liite liite}))

          :default nil)
    (log/debug "Tarkastuksen tallennus suoritettu")))

(defn tallenna-tarkastukset! [db tarkastukset kayttaja]
  (let [kaikki-tarkastukset (reduce conj
                                    (:pistemaiset-tarkastukset tarkastukset)
                                    (:reitilliset-tarkastukset tarkastukset))]
    (doseq [tarkastus kaikki-tarkastukset]
      (tallenna-tarkastus! db tarkastus kayttaja))))