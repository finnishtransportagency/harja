(ns harja-laadunseuranta.tarkastusreittimuunnin.tarkastusreittimuunnin
  "Tämä namespace tarjoaa funktiot Harjan mobiililla laadunseurantatyökalulla tehtyjen reittimerkintöjen
   muuntamiseksi Harja-tarkastukseksi.

   Tärkeimmät funktiot:
   - reittimerkinnat-tarkastuksiksi, jolla varsinainen muunto tehdään
   - tallenna-tarkastukset!, joka tallentaa tarkastukset kantaan"
  (:require [taoensso.timbre :as log]
            [harja-laadunseuranta.tietokanta :as tietokanta]
            [harja-laadunseuranta.kyselyt :as q]
            [harja-laadunseuranta.utils :as utils]
            [harja.kyselyt.tarkastukset :as tark-q]
            [harja-laadunseuranta.tarkastusreittimuunnin.ramppianalyysi :as ramppianalyysi]
            [harja-laadunseuranta.tarkastusreittimuunnin.ymparikaantyminen :as ymparikaantyminen]
            [harja-laadunseuranta.tarkastusreittimuunnin.virheelliset-tiet :as virheelliset-tiet]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [harja.domain.roolit :as roolit]))

(def +kahden-pisteen-valinen-sallittu-aikaero-s+ 180)

(defn- seuraava-mittausarvo-sama? [nykyinen-reittimerkinta
                                   seuraava-reittimerkinta
                                   mittaus-avain]
  (cond
    (nil? (mittaus-avain nykyinen-reittimerkinta))
    (= (nil? (mittaus-avain nykyinen-reittimerkinta))
       (nil? (mittaus-avain seuraava-reittimerkinta)))

    (number? (mittaus-avain nykyinen-reittimerkinta))
    (= (mittaus-avain nykyinen-reittimerkinta)
       (mittaus-avain seuraava-reittimerkinta))

    (vector? (mittaus-avain nykyinen-reittimerkinta))
    (every? #(= (mittaus-avain seuraava-reittimerkinta) %)
            (mittaus-avain nykyinen-reittimerkinta))))

(defn- tarkastus-jatkuu?
  "Ottaa reittimerkinnän ja järjestyksesä seuraavan reittimerkinnän ja kertoo muodostavatko ne loogisen jatkumon,
   toisin sanoen tulkitaanko seuraavan pisteen olevan osa samaa tarkastusta vai ei."
  [nykyinen-reittimerkinta seuraava-reittimerkinta]
  (let [jatkuvat-havainnot-pysyvat-samana? (= (:jatkuvat-havainnot nykyinen-reittimerkinta)
                                              (:jatkuvat-havainnot seuraava-reittimerkinta))
        seuraava-piste-samalla-tiella? (boolean (or
                                                  (nil? (:tr-osoite nykyinen-reittimerkinta))
                                                  (nil? (:tr-osoite seuraava-reittimerkinta))
                                                  (= (get-in nykyinen-reittimerkinta [:tr-osoite :tie])
                                                     (get-in seuraava-reittimerkinta [:tr-osoite :tie]))))
        ei-ajallista-gappia? (let [aikaleima-nykyinen-merkinta (c/from-sql-time (:aikaleima nykyinen-reittimerkinta))
                                   aikaleima-seuraava-merkinta (c/from-sql-time (:aikaleima seuraava-reittimerkinta))]
                               (if (or
                                     (nil? (:aikaleima nykyinen-reittimerkinta))
                                     (nil? (:aikaleima seuraava-reittimerkinta))
                                     (t/before? aikaleima-seuraava-merkinta aikaleima-nykyinen-merkinta))
                                 ;; Kerran törmättiin harvinaiseen tilanteeseen, jossa myöhemmin kirjatun pisteen
                                 ;; aikaleima oli ennen seuraavaa. Tällainen tilanne pitää pystyä käsittelemään
                                 true
                                 (boolean (<= (t/in-seconds (t/interval aikaleima-nykyinen-merkinta
                                                                        aikaleima-seuraava-merkinta))
                                              +kahden-pisteen-valinen-sallittu-aikaero-s+))))
        jatkuvat-mittausarvot-samat? (boolean (and (seuraava-mittausarvo-sama? nykyinen-reittimerkinta seuraava-reittimerkinta :soratie-tasaisuus)
                                                   (seuraava-mittausarvo-sama? nykyinen-reittimerkinta seuraava-reittimerkinta :kiinteys)
                                                   (seuraava-mittausarvo-sama? nykyinen-reittimerkinta seuraava-reittimerkinta :polyavyys)
                                                   (seuraava-mittausarvo-sama? nykyinen-reittimerkinta seuraava-reittimerkinta :sivukaltevuus)))
        seuraavassa-pisteessa-ei-kaannyta-ympari? (not (:ymparikaantyminen? seuraava-reittimerkinta))]

    (when-not jatkuvat-havainnot-pysyvat-samana? (log/debug (:sijainti seuraava-reittimerkinta) "Jatkuvat havainnot muuttuu " (:jatkuvat-havainnot nykyinen-reittimerkinta) " -> " (:jatkuvat-havainnot seuraava-reittimerkinta) ", katkaistaan reitti"))
    (when-not seuraava-piste-samalla-tiella? (log/debug (:sijainti seuraava-reittimerkinta) "Seuraava piste eri tiellä " (get-in nykyinen-reittimerkinta [:tr-osoite :tie]) " -> " (get-in seuraava-reittimerkinta [:tr-osoite :tie]) ", katkaistaan reitti"))
    (when-not ei-ajallista-gappia? (log/debug (:sijainti seuraava-reittimerkinta) "Ajallinen gäppi pisteiden välillä, " (c/from-sql-time (:aikaleima nykyinen-reittimerkinta)) " ja " (c/from-sql-time (:aikaleima seuraava-reittimerkinta)) ", katkaistaan reitti"))
    (when-not jatkuvat-mittausarvot-samat? (log/debug (:sijainti seuraava-reittimerkinta) "Jatkuvat mittausarvot muuttuivat, katkaistaan reitti"))
    (when-not seuraavassa-pisteessa-ei-kaannyta-ympari? (log/debug (:sijainti seuraava-reittimerkinta) "Ympärikääntyminen havaittu, katkaistaan reitti"))

    (boolean
      (and
        ;; Jatkuvat havainnot pysyvät samana myös seuraavassa pisteessä
        jatkuvat-havainnot-pysyvat-samana?
        ;; Seuraava piste on osa samaa tietä. Jos seuraavalle pistelle ei ole pystytty määrittelemään tietä,
        ;; niin oletetaan kuitenkin, että se on osa samaa tarkastusta niin kauan kuin osoite oikeasti vaihtuu
        seuraava-piste-samalla-tiella?
        ;; Edellisen pisteen kirjauksesta ei ole kulunut ajallisesti liian kauan
        ;; Jos on kulunut, emme tiedä, mitä näiden pisteiden välillä on tapahtunut, joten on turvallista
        ;; päättää edellinen tarkastus ja aloittaa uusi.
        ei-ajallista-gappia?
        ;; Jatkuvat mittausarvot pysyvät samana. Soratiemittauksessa mittausarvot voivat olla päällä
        ;; pitkän aikaa ja mittausarvot tallentuvat tällöin usealle pisteelle. Jos jokin mittausarvoista muuttuu,
        ;; halutaan tarkastuskin katkaista, jotta samat päällä olevat mittausarvot muodostavat aina oman reitin.
        ;; Edellisessä merkinnässä tehty mittaus on joko numero tai vector numeroita, jos kyseessä yhdistetty
        ;; reittimerkintä
        jatkuvat-mittausarvot-samat?
        ;; Seuraava piste ei aiheuta ympärikääntymistä. Jos aiheuttaa, reitti tulee katkaista.
        seuraavassa-pisteessa-ei-kaannyta-ympari?))))

(defn- yhdista-reittimerkinnan-kaikki-havainnot
  "Yhdistää reittimerkinnän pistemäiset havainnot ja jatkuvat havainnot."
  [reittimerkinta]
  (if (empty? (:jatkuvat-havainnot reittimerkinta))
    (filterv some? [(:pistemainen-havainto reittimerkinta)])
    (filterv some? (conj (:jatkuvat-havainnot reittimerkinta)
                         (:pistemainen-havainto reittimerkinta)))))

(defn keskiarvo
  [arvo]
  (cond
    (nil? arvo)
    nil

    (number? arvo)
    arvo

    (empty? arvo)
    nil

    :default ;; vector
    (float (with-precision 3
             (/ (apply + arvo) (count arvo))))))

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

(defn- kasittele-pistemainen-tarkastusreitti
  "Asettaa tieosoitteen paatepisteen (losa / let) nilliksi jos sama kuin lahtopiste (aosa / aet)"
  [osoite]
  (if (and (= (:aosa osoite)
              (:losa osoite))
           (= (:aet osoite)
              (:let osoite)))
    (-> osoite
        (assoc :losa nil :let nil))
    osoite))

(defn- muodosta-tarkastuksen-lopullinen-tr-osoite [reittimerkinta]
  (let [tarkastuksen-reitti (:sijainnit reittimerkinta)
        lahtopiste (:tr-osoite (first tarkastuksen-reitti))
        paatepiste (:tr-osoite (last tarkastuksen-reitti))
        koko-tarkastuksen-tr-osoite {:tie (:tie lahtopiste)
                                     :aosa (:aosa lahtopiste)
                                     :aet (:aet lahtopiste)
                                     :losa (or (:losa paatepiste) (:aosa paatepiste))
                                     :let (or (:let paatepiste) (:aet paatepiste))}
        ;; Pistemäisessä sekä lähtö- että paatepiste ovat samat, jolloin losa ja let ovat samat. Käsitellään ne:
        koko-tarkastuksen-tr-osoite (kasittele-pistemainen-tarkastusreitti koko-tarkastuksen-tr-osoite)]

    koko-tarkastuksen-tr-osoite))

(defn- reittimerkinta-tarkastukseksi
  "Muuntaa reittimerkinnän Harja-tarkastukseksi.
   Reittimerkintä voi olla joko yksittäinen (pistemäinen) reittimerkintä tai
   jatkuvista havainnoista kasattu, yhdistetty reittimerkintä."
  [reittimerkinta]
  (let [kentan-arvo-vectorina (fn [reittimerkinta avain]
                                (cond
                                  (nil? (avain reittimerkinta))
                                  []

                                  (number? (avain reittimerkinta))
                                  [(avain reittimerkinta)]

                                  (vector? (avain reittimerkinta))
                                  (avain reittimerkinta)))
        mittausarvojen-keskiarvo (fn [reittimerkinta mittaus-avain]
                                   (cond
                                     (nil? (mittaus-avain reittimerkinta))
                                     nil

                                     (number? (mittaus-avain reittimerkinta))
                                     (mittaus-avain reittimerkinta)

                                     (vector? (mittaus-avain reittimerkinta))
                                     (keskiarvo (mittaus-avain reittimerkinta))))]
    (as-> {;; Pistemäisessä aika on aina tämä, koska yksi piste.
           ;; Yhdistetyttä merkinnässä tässä on viimeisimmän merkinnän aika
           :aika (:aikaleima reittimerkinta)
           :tyyppi (paattele-tarkastustyyppi reittimerkinta)
           :tarkastusajo (:tarkastusajo reittimerkinta)
           ;; Reittimerkintöjen id:t, joista tämä tarkastus muodostuu
           :reittimerkinta-idt (kentan-arvo-vectorina reittimerkinta :id)
           :sijainnit (or (:sijainnit reittimerkinta) [{:sijainti (:sijainti reittimerkinta)
                                                        :tr-osoite (:tr-osoite reittimerkinta)}])
           :liitteet (kentan-arvo-vectorina reittimerkinta :kuva)
           :vakiohavainnot (yhdista-reittimerkinnan-kaikki-havainnot reittimerkinta)
           :havainnot (:kuvaus reittimerkinta)
           :talvihoitomittaus {:talvihoitoluokka nil
                               :lumimaara (mittausarvojen-keskiarvo reittimerkinta :lumisuus)
                               :tasaisuus (mittausarvojen-keskiarvo reittimerkinta :talvihoito-tasaisuus)
                               :kitka (mittausarvojen-keskiarvo reittimerkinta :kitkamittaus)
                               :ajosuunta nil
                               :lampotila_ilma (mittausarvojen-keskiarvo reittimerkinta :lampotila)
                               :lampotila_tie nil}
           :soratiemittaus {:hoitoluokka nil
                            :tasaisuus (mittausarvojen-keskiarvo reittimerkinta :soratie-tasaisuus)
                            :kiinteys (mittausarvojen-keskiarvo reittimerkinta :kiinteys)
                            :polyavyys (mittausarvojen-keskiarvo reittimerkinta :polyavyys)
                            :sivukaltevuus (mittausarvojen-keskiarvo reittimerkinta :sivukaltevuus)}
           :laadunalitus (boolean (:laadunalitus reittimerkinta))}
          tarkastus
          (assoc tarkastus :lopullinen-tr-osoite (muodosta-tarkastuksen-lopullinen-tr-osoite tarkastus)))))

(defn viimeinen-indeksi [sekvenssi]
  (- (count sekvenssi) 1))

(defn- keraa-seuraavan-pisteen-arvot
  "Ottaa reittimerkinnän ja järjestyksessä seuraavan reittimerkinnän.
   Lisää seuraavan mittauksen tiedot edelliseen (numero tai vector)"
  [reittimerkinta seuraava-reittimerkinta arvo-avain]
  (if (nil? (arvo-avain seuraava-reittimerkinta))
    reittimerkinta

    (cond (nil? (arvo-avain reittimerkinta)) ;; Aseta arvoksi seuraava mittaus
          (assoc reittimerkinta arvo-avain (arvo-avain seuraava-reittimerkinta))

          (number? (arvo-avain reittimerkinta)) ;; Muunna vectoriksi
          (assoc reittimerkinta arvo-avain [(arvo-avain reittimerkinta)
                                            (arvo-avain seuraava-reittimerkinta)])

          (vector? (arvo-avain reittimerkinta)) ;; Lisää seuraava arvo vectoriin
          (assoc reittimerkinta arvo-avain (conj (arvo-avain reittimerkinta)
                                                 (arvo-avain seuraava-reittimerkinta))))))

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

(defn- keraa-seuraavan-pisteen-aikaleima
  "Ottaa reittimerkinnän ja järjestyksessä seuraavan reittimerkinnän.
   Asettaa aikaleimaksi seuraavan merkinnän aikaleiman."
  [reittimerkinta seuraava-reittimerkinta]
  (if-let [seuraava-aikaleima (:aikaleima seuraava-reittimerkinta)]
    (assoc reittimerkinta :aikaleima seuraava-aikaleima)
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
              (as-> viimeisin-yhdistetty-reittimerkinta edellinen
                    (keraa-seuraavan-pisteen-sijainti edellinen seuraava-merkinta)
                    (keraa-seuraavan-pisteen-laadunalitus edellinen seuraava-merkinta)
                    (keraa-seuraavan-pisteen-aikaleima edellinen seuraava-merkinta)
                    (keraa-seuraavan-pisteen-arvot edellinen seuraava-merkinta :talvihoito-tasaisuus)
                    (keraa-seuraavan-pisteen-arvot edellinen seuraava-merkinta :lumisuus)
                    (keraa-seuraavan-pisteen-arvot edellinen seuraava-merkinta :kitkamittaus)
                    (keraa-seuraavan-pisteen-arvot edellinen seuraava-merkinta :soratie-tasaisuus)
                    (keraa-seuraavan-pisteen-arvot edellinen seuraava-merkinta :kiinteys)
                    (keraa-seuraavan-pisteen-arvot edellinen seuraava-merkinta :polyavyys)
                    (keraa-seuraavan-pisteen-arvot edellinen seuraava-merkinta :sivukaltevuus)
                    (keraa-seuraavan-pisteen-arvot edellinen seuraava-merkinta :lampotila)
                    (keraa-seuraavan-pisteen-arvot edellinen seuraava-merkinta :kuva)
                    (keraa-seuraavan-pisteen-arvot edellinen seuraava-merkinta :id)
                    (keraa-reittimerkintojen-kuvaukset edellinen seuraava-merkinta)))
            ;; Uusi tarkastus alkaa
            (conj reittimerkinnat seuraava-merkinta)))))
    []
    reittimerkinnat))

(defn- pistemainen-havainto?
  [reittimerkinta]
  (boolean (and (or (:pistemainen-havainto reittimerkinta)
                    ;; Kuvan tai tekstiä sisältävän merkinnän pitäisi olla aina
                    ;; lomakkeelta kirjattu pistemäinen yleishavainto,
                    ;; mutta varmistetaan nyt kuitenkin
                    (:kuva reittimerkinta)
                    (:kuvaus reittimerkinta)))))

(defn- toiseen-merkintaan-liittyva-merkinta?
  [reittimerkinta]
  (some? (:liittyy-merkintaan reittimerkinta)))

(defn- reittimerkinnat-reitillisiksi-tarkastuksiksi
  "Käy annetut reittimerkinnät läpi ja muodostaa niistä reitilliset tarkastukset"
  [reittimerkinnat]
  (let [jatkuvat-reittimerkinnat (filter #(and (not (pistemainen-havainto? %))
                                               (not (toiseen-merkintaan-liittyva-merkinta? %)))
                                         reittimerkinnat)
        yhdistetyt-reittimerkinnat (yhdista-jatkuvat-reittimerkinnat jatkuvat-reittimerkinnat)]
    (mapv reittimerkinta-tarkastukseksi yhdistetyt-reittimerkinnat)))

(defn- reittimerkinnat-pistemaisiksi-tarkastuksiksi
  "Käy annetut reittimerkinnät läpi ja muodostaa niistä pistemäiset tarkastukset"
  [reittimerkinnat]
  (let [pistemaiset-reittimerkinnat (filter #(and (pistemainen-havainto? %)
                                                  (not (toiseen-merkintaan-liittyva-merkinta? %)))
                                            reittimerkinnat)]
    (mapv reittimerkinta-tarkastukseksi pistemaiset-reittimerkinnat)))

(defn- liita-tarkastukseen-liittyvat-merkinnat
  "Etsii ja lisää tarkastukseen siihen liittyvät tiedot.
   Jos liittyviä tietoja ei ole, tarkastus palautuu sellaisenaan."
  [tarkastus liittyvat-merkinnat]
  (let [tarkastukseen-liittyvat-merkinnat (filter
                                            #((into #{} (:reittimerkinta-idt tarkastus))
                                               (:liittyy-merkintaan %))
                                            liittyvat-merkinnat)]
    (if (empty? tarkastukseen-liittyvat-merkinnat)
      tarkastus
      (merge tarkastus
             ;; Lisätään mahdolliset kuvaukset perään rivinvaihdoilla erotettuna
             {:havainnot (let [kuvaukset (map :kuvaus tarkastukseen-liittyvat-merkinnat)]
                           (if (empty? kuvaukset)
                             (:havainnot tarkastus)
                             (str (when-let [olemassaoleva-kuvaus (:havainnot tarkastus)]
                                    (str olemassaoleva-kuvaus) "\n")
                                  (str/join "\n" (map :kuvaus tarkastukseen-liittyvat-merkinnat)))))
              ;; Lisätään mahdolliset kuvaliitteet tarkastukseen
              :liitteet (let [kuvat (map :kuva tarkastukseen-liittyvat-merkinnat)]
                          (if (empty? kuvat)
                            (:liitteet tarkastus)
                            (apply conj (:liitteet tarkastus) kuvat)))
              ;; Merkitään tarkastukseen laadunalitus jos se, tai mikä tahansa liittyvistä merkinnöistä,
              ;; sisältää laadunalituksen
              :laadunalitus (let [laadunalitukset (map :laadunalitus tarkastukseen-liittyvat-merkinnat)
                                  _ (log/debug "Laadunalitukset: " (pr-str laadunalitukset))]
                              (if (empty? laadunalitukset)
                                (:laadunalitus tarkastus)
                                (boolean (some true? (conj laadunalitukset (:laadunalitus tarkastus))))))}))))

(defn- liita-tarkastuksiin-lomakkeelta-kirjatut-tiedot
  "Ottaa mapin, jossa on reittimerkinnöistä muunnetut Harja-tarkastukset (pistemäiset ja reitilliset),
   sekä toisiin merkintöihin liittyvät merkinnät. Etsii ja lisää tarkastuksiin niihin kirjatut
   liittyvät tiedot."
  [tarkastukset liittyvat-merkinnat]
  {:reitilliset-tarkastukset (mapv #(liita-tarkastukseen-liittyvat-merkinnat % liittyvat-merkinnat)
                                   (:reitilliset-tarkastukset tarkastukset))
   :pistemaiset-tarkastukset (mapv #(liita-tarkastukseen-liittyvat-merkinnat % liittyvat-merkinnat)
                                   (:pistemaiset-tarkastukset tarkastukset))})

(defn- valmistele-merkinnat-kasittelyyn [merkinnat optiot]
  (as->
    merkinnat m
    (if (:analysoi-rampit? optiot) (ramppianalyysi/korjaa-virheelliset-rampit merkinnat) m)
    (if (:analysoi-ymparikaantymiset? optiot) (ymparikaantyminen/lisaa-tieto-ymparikaantymisesta m) m)
    (if (:analysoi-virheelliset-tiet? optiot) (virheelliset-tiet/korjaa-virheelliset-tiet m) m)))

(defn reittimerkinnat-tarkastuksiksi
  "Reittimerkintämuunnin, joka käy reittimerkinnät läpi ja palauttaa mapin, jossa reittimerkinnät muutettu
   reitillisiksi ja pistemäisiksi Harja-tarkastuksiksi.

   Optiot on mappi:
   - analysoi-rampit?                         Korjaa virheellisesti rampille projisoituneet pisteet takaisin moottoritielle.
                                              Oletus: true.
   - analysoi-ymparikaantymiset?              Katkaisee reitit pisteistä, joissa havaitaan selkeä ympärikääntyminen.
                                              Oletus: true.
   - analysoi-virheelliset-tiet?              Projisoi tien pisteet edelliselle tielle, jos tielle on osunut vain pieni määrä
                                              pisteitä ja ne kaikki ovat lähellä edellistä tietä. Tarkoituksena korjata
                                              tilanteet, joissa muutama yksittäinen piste osuu eri tielle esim. siltojen
                                              ja risteysten kohdalla.
                                              Oletus: true."
  ([tr-osoitteelliset-reittimerkinnat]
   (reittimerkinnat-tarkastuksiksi tr-osoitteelliset-reittimerkinnat {:analysoi-rampit? true
                                                                      :analysoi-ymparikaantymiset? true
                                                                      :analysoi-virheelliset-tiet? true}))
  ([tr-osoitteelliset-reittimerkinnat optiot]
   (let [kasiteltavat-merkinnat (valmistele-merkinnat-kasittelyyn tr-osoitteelliset-reittimerkinnat optiot)
         tarkastukset {:reitilliset-tarkastukset (reittimerkinnat-reitillisiksi-tarkastuksiksi
                                                   kasiteltavat-merkinnat)
                       :pistemaiset-tarkastukset (reittimerkinnat-pistemaisiksi-tarkastuksiksi
                                                   kasiteltavat-merkinnat)}
         liittyvat-merkinnat (filterv toiseen-merkintaan-liittyva-merkinta?
                                      kasiteltavat-merkinnat)
         tarkastukset-lomaketiedoilla (liita-tarkastuksiin-lomakkeelta-kirjatut-tiedot tarkastukset
                                                                                       liittyvat-merkinnat)]
     tarkastukset-lomaketiedoilla)))

;; -------- Tarkastuksen tallennus kantaan --------

(defn- muodosta-tarkastuksen-geometria
  [db {:keys [tie aosa aet losa let] :as tieosoite}]
  (when (and tie aosa aet)
    (:geom (first (q/tr-osoitteelle-viiva
                    db
                    {:tr_numero tie
                     :tr_alkuosa aosa
                     :tr_alkuetaisyys aet
                     :tr_loppuosa (or losa aosa)
                     :tr_loppuetaisyys (or let aet)})))))

(defn luo-kantaan-tallennettava-tarkastus
  "Ottaa reittimerkintämuuntimen luoman tarkastuksen ja palauttaa mapin,
   jolla tarkastus voidaan lisätä kantaan."
  [db tarkastus kayttaja]
  (let [geometria (muodosta-tarkastuksen-geometria db (:lopullinen-tr-osoite tarkastus))]
    (assoc tarkastus
      :tarkastaja (str (:etunimi kayttaja) " " (:sukunimi kayttaja))
      :tr_numero (:tie (:lopullinen-tr-osoite tarkastus))
      :tr_alkuosa (:aosa (:lopullinen-tr-osoite tarkastus))
      :tr_alkuetaisyys (:aet (:lopullinen-tr-osoite tarkastus))
      :tr_loppuosa (:losa (:lopullinen-tr-osoite tarkastus))
      :tr_loppuetaisyys (:let (:lopullinen-tr-osoite tarkastus))
      :sijainti geometria
      :lahde "harja-ls-mobiili")))

(defn- tallenna-tarkastus! [db tarkastus kayttaja]
  (log/debug "Aloitetaan tarkastuksen tallennus")
  (let [tarkastus (luo-kantaan-tallennettava-tarkastus db tarkastus kayttaja)
        _ (q/luo-uusi-tarkastus<! db
                                  (merge tarkastus
                                         {:luoja (:id kayttaja)
                                          :nayta_urakoitsijalle (roolit/urakoitsija? kayttaja)}))
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
    (doseq [liite (:liitteet tarkastus)]
      (log/debug "Tallennetaan liite (yksi monesta): " (pr-str liite))
      (q/luo-uusi-tarkastus-liite<! db
                                    {:tarkastus tarkastus-id
                                     :liite liite}))
    (log/debug "Tarkastuksen tallennus suoritettu")))

(defn tallenna-tarkastukset!
  "Tallentaa reittimerkintämuuntimen luomat tarkastukset kantaan"
  [db tarkastukset kayttaja]
  (let [kaikki-tarkastukset (reduce conj
                                    (:pistemaiset-tarkastukset tarkastukset)
                                    (:reitilliset-tarkastukset tarkastukset))]
    (doseq [tarkastus kaikki-tarkastukset]
      (tallenna-tarkastus! db tarkastus kayttaja))))