(ns harja-laadunseuranta.tarkastukset
  (:require [taoensso.timbre :as log]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [harja-laadunseuranta.utils :as utils]
            [harja.kyselyt.tarkastukset :as tark-q]
            [clojure.string :as str]))

(def db tietokanta/db)
(def +reittimerkinnan-tieprojisoinnin-treshold+ 250)

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

(defn- tarkastus-jatkuu?
  "Ottaa reittimerkinnän ja järjestyksesä seuraavan reittimerkinnän ja kertoo muodostavatko ne loogisen jatkumon,
  toisin sanoen tulkitaanko seuraavan pisteen olevan osa samaa tarkastusta vai ei."
  [nykyinen-reittimerkinta seuraava-reittimerkinta]
  (and
    ;; Jatkuvat havainnot pysyvät samana myös seuraavassa pisteessä
    (= (:jatkuvat-havainnot nykyinen-reittimerkinta) (:jatkuvat-havainnot seuraava-reittimerkinta))
    ;; Seuraava piste on osa samaa tietä ja tieosaa. Jos seuraavalle pistelle ei ole pystytty määrittelemään tietä,
    ;; niin oletetaan kuitenkin, että se on osa samaa tarkastusta
    (not (:laadunalitus seuraava-reittimerkinta))
    (or (nil? (:tr-osoite seuraava-reittimerkinta))
        (= (get-in nykyinen-reittimerkinta [:tr-osoite :tie]) (get-in seuraava-reittimerkinta [:tr-osoite :tie])))
    ;; Seuraava piste ei aiheuta reitin kääntymistä ympäri
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
    (with-precision 3
      (/ (apply + numerot) (count numerot)))))

(defn- paattele-tarkastustyyppi [reittimerkinta]
  (let [jatkuvat-havainnot (map @q/vakiohavainto-idt (:jatkuvat-havainnot reittimerkinta))]
    (if (or (some #{:yleishavainto} jatkuvat-havainnot)
            (:pistemainen-havainto reittimerkinta))
      "laatu"
      (if-not jatkuvat-havainnot
        (if (= 1 (:tyyppi reittimerkinta))
          "talvihoito"
          "soratie")
        "laatu"))))

(defn- reittimerkinta-tarkastukseksi
  "Muuntaa reittimerkinnän Harja-tarkastukseksi"
  [reittimerkinta]
  {:aika (:aikaleima reittimerkinta)
   :tyyppi (paattele-tarkastustyyppi reittimerkinta)
   :tarkastusajo (:tarkastusajo reittimerkinta)
   :sijainnit (or (:sijainnit reittimerkinta) [{:sijainti (:sijainti reittimerkinta)
                                                :tr-osoite (:tr-osoite reittimerkinta)}])
   :liite (:kuva reittimerkinta)
   :vakiohavainnot (yhdista-reittimerkinnan-kaikki-havainnot reittimerkinta)
   :havainnot (:kuvaus reittimerkinta)
   :talvihoitomittaus {:talvihoitoluokka nil
                       :lumimaara (:lumisuus reittimerkinta)
                       :tasaisuus (:tasaisuus reittimerkinta)
                       :kitka (or (keskiarvo (:kitkamittaukset reittimerkinta))
                                  (:kitkamittaus reittimerkinta))
                       :ajosuunta nil
                       :lampotila_ilma (:lampotila reittimerkinta)
                       :lampotila_tie nil}
   :soratiemittaus {:hoitoluokka nil
                    :tasaisuus (:tasaisuus reittimerkinta)
                    :kiinteys (:kiinteys reittimerkinta)
                    :polyavyys (:polyavyys reittimerkinta)
                    :sivukaltevuus (:sivukaltevuus reittimerkinta)}
   :laadunalitus (or (:laadunalitus reittimerkinta) false)})

(defn viimeinen-indeksi [sekvenssi]
  (- (count sekvenssi) 1))

(defn- lisaa-reittimerkintaan-seuraavan-pisteen-kitka
  "Ottaa reittimerkinnän ja järjestyksessä seuraavan reittimerkinnän. Lisää seuraavan kitkan tiedot edelliseen."
  [reittimerkinta seuraava-reittimerkinta]
  (if (nil? (:kitkamittaus seuraava-reittimerkinta))
    reittimerkinta
    (if (nil? (:kitkamittaukset reittimerkinta))
      (-> reittimerkinta
          (assoc :kitkamittaukset (keep identity [(:kitkamittaus reittimerkinta) (:kitkamittaus seuraava-reittimerkinta)]))
          (dissoc :kitkamittaus))
      (assoc reittimerkinta :kitkamittaukset (conj (:kitkamittaukset reittimerkinta) (:kitkamittaus seuraava-reittimerkinta))))))

(defn- lisaa-reittimerkintaan-seuraavan-pisteen-sijainti
  "Ottaa reittimerkinnän ja järjestyksessä seuraavan reittimerkinnän. Lisää seuraavan sijainnin ja TR-osoitteen tiedot edelliseen."
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

(defn- keraa-reittimerkintojen-kuvaukset
  "Yhdistää samalla jatkuvalla havainnolla olevat kuvauskentät yhteen"
  [reittimerkinta seuraava-reittimerkinta]
  (if (nil? (:kuvaus seuraava-reittimerkinta))
    reittimerkinta
    (assoc reittimerkinta :kuvaus (str (when-let [k (:kuvaus reittimerkinta)]
                                         (str k "\n")) (:kuvaus seuraava-reittimerkinta)))))

(defn- keraa-mittaukset
  [reittimerkinta seuraava-reittimerkinta]
  (merge reittimerkinta (utils/select-non-nil-keys seuraava-reittimerkinta [:tasaisuus :kiinteys :polyavyys :sivukaltevuus :lampotila :kuva :lumisuus])))

(defn- yhdista-jatkuvat-reittimerkinnat
  "Ottaa joukon reittimerkintöjä ja yhdistää ne yhdeksi loogiseksi jatkumoksi."
  [reittimerkinnat]
  (reduce
    (fn [reittimerkinnat seuraava-merkinta]
      (if (empty? reittimerkinnat)
        (conj reittimerkinnat seuraava-merkinta)
        (let [viimeisin-yhdistetty-reittimerkinta (last reittimerkinnat)]
          (if (tarkastus-jatkuu? viimeisin-yhdistetty-reittimerkinta seuraava-merkinta)
            ;; Sama tarkastus jatkuu, ota seuraavan mittauksen tiedot ja lisää ne viimeisimpään reittimerkintään
            (assoc reittimerkinnat (viimeinen-indeksi reittimerkinnat)
                                   (-> viimeisin-yhdistetty-reittimerkinta
                                       (lisaa-reittimerkintaan-seuraavan-pisteen-sijainti seuraava-merkinta)
                                       (lisaa-reittimerkintaan-seuraavan-pisteen-kitka seuraava-merkinta)
                                       (keraa-reittimerkintojen-kuvaukset seuraava-merkinta)
                                       (keraa-mittaukset seuraava-merkinta)))
            ;; Uusi tarkastus alkaa
            (conj reittimerkinnat seuraava-merkinta)))))
    []
    reittimerkinnat))

(defn- pistemainen-havainto?
  "Onko havainto pistemäinen?"
  [reittimerkinta]
  (or (:pistemainen-havainto reittimerkinta)
      (and (:kuva reittimerkinta) (empty? (:jatkuvat-havainnot reittimerkinta)))
      #_(:kuvaus reittimerkinta)))

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

(defn lisaa-reittimerkinnalle-tieosoite [reittimerkinta]
  (if (:tie reittimerkinta)
    (-> reittimerkinta
        (assoc :tr-osoite (select-keys reittimerkinta [:tie :aosa :aet]))
        (dissoc :tie :aosa :aet))
    reittimerkinta))

(defn lisaa-tarkastuksille-urakka-id [{:keys [reitilliset-tarkastukset pistemaiset-tarkastukset]} urakka-id]
  {:reitilliset-tarkastukset (mapv #(assoc % :urakka urakka-id) reitilliset-tarkastukset)
   :pistemaiset-tarkastukset (mapv #(assoc % :urakka urakka-id) pistemaiset-tarkastukset)})

(defn lisaa-reittimerkinnoille-tieosoite [reittimerkinnat]
  (mapv lisaa-reittimerkinnalle-tieosoite reittimerkinnat))

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
                                     :losa (:aosa paatepiste)
                                     :let (:aet paatepiste)}]
    (assoc tarkastus
                                        ;:sijainti (:geometria koko-tarkastuksen-tr-osoite)
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
  (let [tarkastus (luo-tallennettava-tarkastus tarkastus kayttaja)
        geometria (hae-tallennettavan-tarkastuksen-sijainti db tarkastus)
        tarkastus (assoc tarkastus :sijainti geometria)
        _ (q/luo-uusi-tarkastus<! db
                                  (merge tarkastus
                                         {:luoja (:id kayttaja)}))
        tarkastus-id (tark-q/luodun-tarkastuksen-id db )]
    (doseq [vakiohavainto-id (:vakiohavainnot tarkastus)]
      (q/luo-uusi-tarkastuksen-vakiohavainto<! db
                                               {:tarkastus tarkastus-id
                                                :vakiohavainto vakiohavainto-id}))
    (when (:talvihoitomittaus tarkastus)
      (q/luo-uusi-talvihoitomittaus<! db
                                      (merge (:talvihoitomittaus tarkastus)
                                             {:tarkastus tarkastus-id})))
    (when (:soratiemittaus tarkastus)
      (q/luo-uusi-soratiemittaus<! db
                                   (merge (:soratiemittaus tarkastus)
                                          {:tarkastus tarkastus-id})))
    (when (:liite tarkastus)
      (q/luo-uusi-tarkastus-liite<! db
                                    {:tarkastus tarkastus-id
                                     :liite (:liite tarkastus)}))))

(defn tallenna-tarkastukset! [db tarkastukset kayttaja]
  (let [kaikki-tarkastukset (reduce conj
                                    (:pistemaiset-tarkastukset tarkastukset)
                                    (:reitilliset-tarkastukset tarkastukset))]
    (doseq [tarkastus kaikki-tarkastukset]
     (tallenna-tarkastus! db tarkastus kayttaja))))
