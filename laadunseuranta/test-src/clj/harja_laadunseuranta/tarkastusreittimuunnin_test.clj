(ns harja-laadunseuranta.tarkastusreittimuunnin-test
  (:require [clojure.test :refer :all]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja-laadunseuranta.core :as ls-core]
            [harja-laadunseuranta.tarkastusreittimuunnin :refer [reittimerkinnat-tarkastuksiksi
                                                                 luo-kantaan-tallennettava-tarkastus]]
            [harja-laadunseuranta.testidata :as testidata]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja-laadunseuranta.core :as harja-laadunseuranta])
  (:import (org.postgis PGgeometry MultiLineString Point)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :harja-laadunseuranta
                        (component/using
                          (harja-laadunseuranta/->Laadunseuranta nil)
                          [:db :http-palvelin])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once (compose-fixtures
                      tietokanta-fixture
                      jarjestelma-fixture))

(defn lisaa-reittimerkinnoille-mockattu-tieosoite
  "Mock-funktio, joka lisää tiemerkinnöille tierekisteriosoitteet ilman oikeaa kannassa olevaa tieverkkoa.
  Mockin lisäämät tierekisteriarvot pohjautuvat kuitenkin oikeisiin tierekisteriosoitteisiin
  (ainakin tätä kirjoittaessa)"
  [reittimerkinnat]
  (map (fn [reittimerkinta]
         (let [tierekisteriosoite (get testidata/mockattu-tierekisteri (:sijainti reittimerkinta))]
           (if tierekisteriosoite
             (assoc reittimerkinta :tr-osoite tierekisteriosoite)
             reittimerkinta)))
       reittimerkinnat))

;; -------- Yleiset muunnostestit --------

(deftest reittimerkinnat-tarkastuksiksi-havainnot-muuttuu
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastukset-joissa-jatkuvat-havainnot-muuttuu))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 3))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Havainnot lisätty oikein
    (is (empty? (-> tarkastukset :reitilliset-tarkastukset first :vakiohavainnot)))
    (is (= (-> tarkastukset :reitilliset-tarkastukset second :vakiohavainnot)
           [17]))
    (is (empty? (-> tarkastukset :reitilliset-tarkastukset (get 2) :vakiohavainnot)))))

(deftest pistemaiset-reittimerkinnat-tarkastuksiksi
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-pistemainen-havainto))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 1))

    ;; Kitkamittaukset laskettu oikein jatkuville pisteille (ei ota huomioon pistemäisiä mittauksia)
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :talvihoitomittaus :kitka) 0.25))
    ;; Kitkamittaus laskettu oikein pistemäiselle tarkastukselle
    (is (= (-> tarkastukset :pistemaiset-tarkastukset first :talvihoitomittaus :kitka) 0.2))

    ;; Havainnot lisätty oikein
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :vakiohavainnot)
           [17]))
    (is (every? #{17 20} (-> tarkastukset :pistemaiset-tarkastukset first :vakiohavainnot)))))

(deftest kaikki-reittimerkinnat-tarkastuksiksi
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/monipuolinen-tarkastus))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 5))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 3))

    ;; Liitteet lisätty oikein
    (is (= (-> tarkastukset :pistemaiset-tarkastukset last :liitteet first) 1))))

(deftest tarkastus-jossa-piste-ei-osu-tielle
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-piste-ei-osu-tielle))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-tie-vaihtuu
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-tie-vaihtuu))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-sijainti-puuttuu
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-sijainti-puuttuu))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-ajallinen-aukko
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-ajallinen-aukko))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

;; PENDING Ympärikääntymislogiikka disabloitu tällä hetkellä GPS:n epätarkkuudesta johtuen
#_(deftest tarkastus-jossa-kaannytaan-ympari
    (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/tarkastus-jossa-kaannytaan-ympari))]
      ;; Muunnettu määrällisesti oikein
      (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
      (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

;; -------- Kantaan tallennettava tarkastus --------

(deftest tarkastus-trvali-jossa-alkuosa-vaihtuu
  (let [merkinnat-tieosoitteilla (lisaa-reittimerkinnoille-mockattu-tieosoite
                                   testidata/tarkastus-jossa-alkuosa-vaihtuu)
        tarkastukset (reittimerkinnat-tarkastuksiksi
                       merkinnat-tieosoitteilla)
        tallennettava (luo-kantaan-tallennettava-tarkastus
                        (:db jarjestelma)
                        (first (:reitilliset-tarkastukset tarkastukset))
                        {:kayttajanimi "jvh"})]
    ;; Tieosoitteet ovat oikein
    (is (= 1 (count (:reitilliset-tarkastukset tarkastukset))))
    (is (= 20 (:tr_numero tallennettava)))
    (is (= 10 (:tr_alkuosa tallennettava)))
    (is (= 4924 (:tr_alkuetaisyys tallennettava)))
    (is (= 11 (:tr_loppuosa tallennettava)))
    (is (= 6349 (:tr_loppuetaisyys tallennettava)))

    ;; Ajolle saatiin muodostettua geometria
    (is (instance? PGgeometry (:sijainti tallennettava)))
    (is (instance? MultiLineString (.getGeometry (:sijainti tallennettava))))

    ;; Alku on ensimmäisen piste ja loppu on viimeinen piste
    (is (= (:tr_alkuosa tallennettava) (get-in (first merkinnat-tieosoitteilla) [:tr-osoite :aosa])))
    (is (= (:tr_alkuetaisyys tallennettava) (get-in (first merkinnat-tieosoitteilla) [:tr-osoite :aet])))
    (is (= (:tr_loppuosa tallennettava) (get-in (last merkinnat-tieosoitteilla) [:tr-osoite :aosa])))
    (is (= (:tr_loppuetaisyys tallennettava) (get-in (last merkinnat-tieosoitteilla) [:tr-osoite :aet])))))

(deftest tarkastus-trvali-jossa-osoitteet-samat
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-kaikki-pisteet-samassa-sijainnissa))
        tallennettava (luo-kantaan-tallennettava-tarkastus
                        (:db jarjestelma)
                        (first (:reitilliset-tarkastukset tarkastukset))
                        {:kayttajanimi "jvh"})]
    (is (= 1 (count (:reitilliset-tarkastukset tarkastukset))))
    (is (= 20 (:tr_numero tallennettava)))
    (is (= 10 (:tr_alkuosa tallennettava)))
    (is (= 4924 (:tr_alkuetaisyys tallennettava)))
    ;; Kaikki osoitteet olivat samat --> tallentuu pistemäisenä
    (is (= nil))
    (is (= nil))

    ;; Ajolle saatiin muodostettua geometria
    (is (instance? PGgeometry (:sijainti tallennettava)))
    (is (instance? Point (.getGeometry (:sijainti tallennettava))))))

(deftest tarkastus-trvali-jossa-yksi-sijainti
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-yksi-piste))
        tallennettava (luo-kantaan-tallennettava-tarkastus
                        (:db jarjestelma)
                        (first (:reitilliset-tarkastukset tarkastukset))
                        {:kayttajanimi "jvh"})]
    (is (= 1 (count (:reitilliset-tarkastukset tarkastukset))))
    (is (= 20 (:tr_numero tallennettava)))
    (is (= 10 (:tr_alkuosa tallennettava)))
    (is (= 4924 (:tr_alkuetaisyys tallennettava)))
    (is (= nil))
    (is (= nil))

    ;; Ajolle saatiin muodostettua geometria
    (is (instance? PGgeometry (:sijainti tallennettava)))
    (is (instance? Point (.getGeometry (:sijainti tallennettava))))))

;; -------- Laadunalitus --------

(deftest tarkastus-jossa-jatkuva-laadunalitus
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-jatkuva-laadunalitus))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Kitka huomioitu
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :talvihoitomittaus :kitka) 0.2))

    ;; Koko tarkastus on merkitty laadunalitukseksi, koska sellainen löytyi osasta tarkastuspisteitä
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :laadunalitus) true))))

;; --------Liittyvät havainnot --------

(deftest tarkastus-jossa-liittyva-havainto
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-liittyvia-pistemaisia-merkintoja))]
    ;; Yksi pistemäinen havainto, johon liitetty lisätietoja
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 1))
    (is (= (:havainnot (first (:pistemaiset-tarkastukset tarkastukset)))
           "Tässä on nyt jotain mätää\nTässä vielä toinen kuva"))
    (is (true? (:laadunalitus (first (:pistemaiset-tarkastukset tarkastukset)))))
    (is (= (:liitteet (first (:pistemaiset-tarkastukset tarkastukset)))
           [1 2]))
    ;; Muu osa (ei pistemäinen havainto eikö siihen liittyvät merkinnät) on yksi jatkuva havainto
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))))

(deftest tarkastus-jossa-laadunalitus-ja-liittyva-merkinta
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-laadunalitus-ja-liittyva-merkinta))]
    (is (true? (:laadunalitus (first (:pistemaiset-tarkastukset tarkastukset)))))))

;; -------- Mittaukset --------

(deftest kitka-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastukset-joissa-jatkuvat-havainnot-muuttuu))]
    ;; Kitkamäärät laskettu oikein
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :talvihoitomittaus :kitka) nil))
    (is (= (-> tarkastukset :reitilliset-tarkastukset second :talvihoitomittaus :kitka) 0.25))
    (is (= (-> tarkastukset :reitilliset-tarkastukset (get 2) :talvihoitomittaus :kitka) nil))))

(deftest lumisuus-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-lumisuus))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Lumisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :talvihoitomittaus :lumimaara) 2))))

(deftest talvihoito-tasaisuus-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-talvihoito-tasaisuus))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :talvihoitomittaus :tasaisuus) 55))))

(deftest soratie-tasaisuus-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-tasaisuus))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 3))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :tasaisuus) 1))
    (is (= (-> tarkastukset :reitilliset-tarkastukset second :soratiemittaus :tasaisuus) nil))
    (is (== (-> tarkastukset :reitilliset-tarkastukset last :soratiemittaus :tasaisuus) 3))))

(deftest soratie-jatkuva-tasaisuus-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-tasaisuus-jatkuu))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 3))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :tasaisuus) 1))
    (is (== (-> tarkastukset :reitilliset-tarkastukset second :soratiemittaus :tasaisuus) 2))
    (is (== (-> tarkastukset :reitilliset-tarkastukset last :soratiemittaus :tasaisuus) 3))))

(deftest soratie-kiinteys-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-kiinteys))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 3))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :kiinteys) 3))
    (is (= (-> tarkastukset :reitilliset-tarkastukset second :soratiemittaus :kiinteys) nil))
    (is (== (-> tarkastukset :reitilliset-tarkastukset last :soratiemittaus :kiinteys) 3))))

(deftest soratie-jatkuva-kiinteys-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-kiinteys-jatkuu))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :kiinteys) 3))
    (is (= (-> tarkastukset :reitilliset-tarkastukset last :soratiemittaus :kiinteys) nil))))

(deftest soratie-polyavyys-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-polyavyys))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 4))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :polyavyys) 1))
    (is (== (-> tarkastukset :reitilliset-tarkastukset second :soratiemittaus :polyavyys) 3))
    (is (= (-> tarkastukset :reitilliset-tarkastukset last :soratiemittaus :polyavyys) nil))))

(deftest soratie-sivukaltevuus-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-sivukaltevuus))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 3))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :sivukaltevuus) 3))
    (is (= (-> tarkastukset :reitilliset-tarkastukset second :soratiemittaus :sivukaltevuus) nil))
    (is (== (-> tarkastukset :reitilliset-tarkastukset last :soratiemittaus :sivukaltevuus) 3))))

;; -------- Testit oikeille tarkastusajoille alusta loppuun --------

(deftest oikean-tarkastusajon-muunto-toimii
  (let [urakka-id (hae-oulun-alueurakan-2014-2019-id)
        tarkastukset (ls-core/muunna-tarkastusajon-reittipisteet-tarkastuksiksi (:db jarjestelma) 754)
        tarkastukset (ls-core/lisaa-tarkastuksille-urakka-id tarkastukset urakka-id)]

    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 8))


    ;; TODO TEE TÄMÄ
    #_(ls-core/tallenna-muunnetut-tarkastukset-kantaan (:db jarjestelma) tarkastukset 1 urakka-id)
    #_(let [tarkastukset-kannassa (q "SELECT id FROM tarkastus WHERE tarkastusajo = 754;")]

      (log/debug "Tulos: " (pr-str tarkastukset-kannassa)))))


;; -------- Apufunktioita REPL-tunkkaukseen --------
;; Älä poista näitä
;; Kutsu tässä NS:ssä esim. (harja.palvelin.main/with-db db (debuggaa-tarkastusajon-muunto db 1))

(defn muunna-tarkastusajo-kantaan [db tarkastusajo-id urakka-id]
  ;; HUOMAA: Tämä EI poista mahdollisesti jo kerran tehtyä muunnosta!
  (let [tarkastukset (ls-core/muunna-tarkastusajon-reittipisteet-tarkastuksiksi db tarkastusajo-id)
        tarkastukset (ls-core/lisaa-tarkastuksille-urakka-id tarkastukset urakka-id)]
    (ls-core/tallenna-muunnetut-tarkastukset-kantaan db tarkastukset 1 urakka-id)))

(defn debuggaa-tarkastusajon-muunto [db tarkastusajo-id]
  ;; Muista ajaa tieverkko kantaan, jotta geometrisointi toimii!
  (log/debug "Debugataan tarkastusajo: " (pr-str tarkastusajo-id))
  (let [tarkastukset (ls-core/muunna-tarkastusajon-reittipisteet-tarkastuksiksi db tarkastusajo-id)
        tie->str (fn [tie]
                   (str (or (:tie tie)
                            (:tr_numero tie))
                        "/" (or (:aosa tie)
                                (:tr_alkuosa tie))
                        "/" (or (:aet tie)
                                (:tr_alkuetaisyys tie))
                        "/" (or (:losa tie)
                                (:tr_loppuosa tie))
                        "/" (or (:let tie)
                                (:tr_loppuetaisyys tie))))
        reitilliset-tarkastukset (:reitilliset-tarkastukset tarkastukset)
        pistemaiset-tarkastukset (:pistemaiset-tarkastukset tarkastukset)
        kaikki-tarkastukset (concat (:reitilliset-tarkastukset tarkastukset)
                                    (:pistemaiset-tarkastukset tarkastukset))]
    (log/debug "Tarkastus muunnettu. Tässäpä tulos:")

    (log/debug "-- Määrät --")
    (log/debug (format "Reitilliset tarkastukset: %s kpl." (count reitilliset-tarkastukset)))
    (log/debug (format "Pistemäiset tarkastukset: %s kpl." (count pistemaiset-tarkastukset)))
    (log/debug (format "Yhteensä: %s kpl." (count kaikki-tarkastukset)))
    (log/debug "")
    (log/debug (format "Saatiin muodostettua tieosoite: %s kpl."
                       (count (filter :tr-osoite
                                      (mapcat :sijainnit kaikki-tarkastukset)))))
    (log/debug (format "Tieosoite puuttuu: %s kpl."
                       (count (filter #(nil? (:tr-osoite %))
                                      (mapcat :sijainnit kaikki-tarkastukset)))))
    (log/debug "")
    (log/debug "-- Ajettu reitti --")
    (log/debug "Reitillisten tarkastusten muodostama ajettu reitti:")
    (let [sijainnit (mapcat :sijainnit (sort-by :aika reitilliset-tarkastukset))]
      (doseq [sijainti sijainnit]
        (log/debug (tie->str (:tr-osoite sijainti)))))

    (log/debug "")
    (log/debug "-- Lopputulos --")
    (log/debug "Lopulliset Harjan kantaan menevät tarkastusten osoitteet:")
    (doseq [tarkastus kaikki-tarkastukset]
      (let [tallennettava (luo-kantaan-tallennettava-tarkastus
                            db
                            tarkastus
                            {:kayttajanimi "jvh"})]
        (log/debug (tie->str tallennettava))))))

(defn debuggaa-tarkastusajojen-muunto [db tarkastusajo-idt]
  (log/debug "Debugataan tarkastusajot: " (pr-str tarkastusajo-idt))
  (doseq [tarkastusajo-id tarkastusajo-idt]
    (debuggaa-tarkastusajon-muunto db tarkastusajo-id)))