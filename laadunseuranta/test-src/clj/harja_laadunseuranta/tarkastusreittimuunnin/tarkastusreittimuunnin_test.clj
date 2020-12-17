(ns harja-laadunseuranta.tarkastusreittimuunnin.tarkastusreittimuunnin-test
  (:require [clojure.test :refer :all]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja-laadunseuranta.core :as ls-core]
            [harja-laadunseuranta.tarkastusreittimuunnin.tarkastusreittimuunnin :refer [reittimerkinnat-tarkastuksiksi
                                                                                        luo-kantaan-tallennettava-tarkastus]]
            [harja-laadunseuranta.testidata :as testidata]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja-laadunseuranta.core :as harja-laadunseuranta]
            [clojure.core :as core]
            [clojure.set :as set]
            [clojure.java.jdbc :as jdbc]
            [harja.domain.tierekisteri :as tr-domain])
  (:import (org.postgis PGgeometry MultiLineString Point)))

(use-fixtures :once (compose-fixtures tietokanta-fixture tietokantakomponentti-fixture))

;; -------- Apurit --------

(defn- tarkista-tallennettavan-tarkastuksen-osoite [tarkastus
                                                    {:keys [tie aosa aet losa let]}]
  (core/let [tallennettava (luo-kantaan-tallennettava-tarkastus
                             (:db jarjestelma)
                             tarkastus
                             {:kayttajanimi "jvh"})]
    (is (instance? MultiLineString (.getGeometry (:sijainti tallennettava))))
    (is (= (:tr_numero tallennettava) tie))
    (is (= (:tr_alkuosa tallennettava) aosa))
    (is (= (:tr_alkuetaisyys tallennettava) aet))
    (is (= (:tr_loppuosa tallennettava) losa))
    (is (= (:tr_loppuetaisyys tallennettava) let))))

(defn- tarkista-tallennettujen-tarkastuksien-osoite [tarkastukset odotetut-tieosat]
  (loop [i 0]
    (tarkista-tallennettavan-tarkastuksen-osoite
      (nth tarkastukset i) (nth odotetut-tieosat i))

    (when (< i (- (count odotetut-tieosat) 1))
      (recur (inc i)))))

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
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
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
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
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
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/monipuolinen-tarkastus))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 5))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 3))

    ;; Liitteet lisätty oikein
    (is (= (-> tarkastukset :pistemaiset-tarkastukset last :liitteet first) 1))))

(deftest tarkastus-jossa-piste-ei-osu-tielle
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-piste-ei-osu-tielle))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-tie-vaihtuu
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-tie-vaihtuu))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-sijainti-puuttuu
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-sijainti-puuttuu))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-sijainti-puuttuu-alusta
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-sijainti-puuttuu-alusta))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-ajallinen-aukko
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-ajallinen-aukko))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-ajallinen-aukko-ja-sitten-tyhja-tie
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-ajallinen-aukko-ja-sitten-tyhja-tie))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 5))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-tyhja-tie-ja-sitten-ajallinen-aukko-tielle
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-tyhja-tie-ja-sitten-ajallinen-aukko-tielle))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 3))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-tyhja-tie-ja-sitten-ajallinen-aukko-ja-sitten-tyhja-tie-ja-sitten-tie
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-tyhja-tie-ja-sitten-ajallinen-aukko-ja-sitten-tyhja-tie-ja-sitten-tie))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 4))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-merkintojen-aikaleimoissa-outouksia
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-yhden-pisteen-aikaleima-on-aiemmin))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

;; -------- Laadunalitus --------

(deftest tarkastus-jossa-jatkuva-laadunalitus
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-jatkuva-laadunalitus))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Kitka huomioitu
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :talvihoitomittaus :kitka) 0.2))

    ;; Koko tarkastus on merkitty laadunalitukseksi, koska sellainen löytyi osasta tarkastuspisteitä
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :laadunalitus) true))))

;; -------- Pisteiden etäisyys suuri --------

(deftest tarkastus-jossa-pisteiden-etaisyys-suuri
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-pisteiden-sijainti-eri))]
    ;; Muunnettu määrällisesti oikein (reitti katkeaa kerran, muodostuu kaksi tarkastusta)
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

;; --------Liittyvät havainnot --------

(deftest tarkastus-jossa-liittyva-havainto
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
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
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-laadunalitus-ja-liittyva-merkinta))]
    (is (true? (:laadunalitus (first (:pistemaiset-tarkastukset tarkastukset)))))))

;; -------- Mittaukset --------

(deftest kitka-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastukset-joissa-jatkuvat-havainnot-muuttuu))]
    ;; Kitkamäärät laskettu oikein
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :talvihoitomittaus :kitka) nil))
    (is (= (-> tarkastukset :reitilliset-tarkastukset second :talvihoitomittaus :kitka) 0.25))
    (is (= (-> tarkastukset :reitilliset-tarkastukset (get 2) :talvihoitomittaus :kitka) nil))))

(deftest lumisuus-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-lumisuus))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Lumisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :talvihoitomittaus :lumimaara) 2))))

(deftest talvihoito-tasaisuus-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-talvihoito-tasaisuus))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :talvihoitomittaus :tasaisuus) 55))))

(deftest soratie-tasaisuus-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
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
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
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
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
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
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-kiinteys-jatkuu-vaikka-gps-sekoaa))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :kiinteys) 3))
    (is (= (-> tarkastukset :reitilliset-tarkastukset last :soratiemittaus :kiinteys) nil))))

(deftest soratie-polyavyys-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
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
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-sivukaltevuus))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 3))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :sivukaltevuus) 3))
    (is (= (-> tarkastukset :reitilliset-tarkastukset second :soratiemittaus :sivukaltevuus) nil))
    (is (== (-> tarkastukset :reitilliset-tarkastukset last :soratiemittaus :sivukaltevuus) 3))))

;; -------- Ajaminen osalta toiselle, kun väliin jää osia jotka ovat maantieteellisesti eri paikassa --------

(deftest osan-vaihto-toimii-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                                                     (lisaa-reittimerkinnoille-mockattu-tieosoite
                                                       testidata/tarkastus-jossa-iso-osamuutos))
        reitilliset (:reitilliset-tarkastukset tarkastukset)
        odotetut-tarkasteut-tieosat [{:tie 70012, :aosa 443, :aet 38 :losa 443 :let 39}
                                     {:tie 70012, :aosa 491, :aet 219 :losa 491 :let 250}]]

    ;; Munnetaan määrällisesti oikein
    (is (= (count reitilliset) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    (tarkista-tallennettujen-tarkastuksien-osoite reitilliset odotetut-tarkasteut-tieosat)))

(deftest osan-vaihto-toimii-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                                                     (lisaa-reittimerkinnoille-mockattu-tieosoite
                                                       testidata/tarkastus-jossa-valiin-jaa-olematon-osa))
        reitilliset (:reitilliset-tarkastukset tarkastukset)]

    ;; Munnetaan määrällisesti oikein
    (is (= (count reitilliset) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

;; -------- Ympärikääntyminen --------

(deftest ymparikaantyminen-katkaistaan-oikein
  (let [db (:db jarjestelma)
        tarkastusajo-id 899
        urakka-id (hae-oulun-alueurakan-2014-2019-id)
        tarkastukset (ls-core/muunna-tarkastusajon-reittipisteet-tarkastuksiksi db tarkastusajo-id)
        tarkastukset (ls-core/lisaa-tarkastuksille-urakka-id tarkastukset urakka-id)
        reitilliset (:reitilliset-tarkastukset tarkastukset)
        pistemaiset (:pistemaiset-tarkastukset tarkastukset)
        odotettu-pistemaisten-maara 0
        odotettu-reitillisten-maara 2
        odotetut-tarkastetut-tieosat
        [{:tie 20, :aosa 41, :aet 4493, :losa 41, :let 4952}
         {:tie 20, :aosa 41, :aet 5031, :losa 41, :let 4493}]]

    ;; Muunnettu määrällisesti oikein
    (is (= (count pistemaiset) odotettu-pistemaisten-maara))
    (is (= (count reitilliset) odotettu-reitillisten-maara))

    ;; Jokainen tallennettava tarkastus muodostetaan tieosoitteen osalta tarkalleen oikein
    (tarkista-tallennettujen-tarkastuksien-osoite reitilliset odotetut-tarkastetut-tieosat)))

(deftest ymparikaantyminen-katkaistaan-oikein-vaikka-ollaan-hetki-paikallaan
  (let [db (:db jarjestelma)
        tarkastusajo-id 900
        urakka-id (hae-oulun-alueurakan-2014-2019-id)
        tarkastukset (ls-core/muunna-tarkastusajon-reittipisteet-tarkastuksiksi db tarkastusajo-id)
        tarkastukset (ls-core/lisaa-tarkastuksille-urakka-id tarkastukset urakka-id)
        reitilliset (:reitilliset-tarkastukset tarkastukset)
        pistemaiset (:pistemaiset-tarkastukset tarkastukset)
        odotettu-pistemaisten-maara 0
        odotettu-reitillisten-maara 2
        odotetut-tarkastetut-tieosat
        [{:tie 20, :aosa 41, :aet 4493, :losa 41, :let 5039}
         {:tie 20, :aosa 41, :aet 5040, :losa 41, :let 4493}]]

    ;; Muunnettu määrällisesti oikein
    (is (= (count pistemaiset) odotettu-pistemaisten-maara))
    (is (= (count reitilliset) odotettu-reitillisten-maara))

    ;; Jokainen tallennettava tarkastus muodostetaan tieosoitteen osalta tarkalleen oikein
    (tarkista-tallennettujen-tarkastuksien-osoite reitilliset odotetut-tarkastetut-tieosat)))

;; -------- Tarkastuksen tallennus kantaan --------

(deftest tarkastus-trvali-jossa-alkuosa-vaihtuu
  (let [merkinnat-tieosoitteilla (lisaa-reittimerkinnoille-mockattu-tieosoite
                                   testidata/tarkastus-jossa-alkuosa-vaihtuu)
        tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
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
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
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
    (is (= nil (:tr_loppuosa tallennettava)))
    (is (= nil (:tr_loppuetaisyys tallennettava)))

    ;; Ajolle saatiin muodostettua geometria
    (is (instance? PGgeometry (:sijainti tallennettava)))
    (is (instance? Point (.getGeometry (:sijainti tallennettava))))))

(deftest tarkastus-trvali-jossa-yksi-sijainti
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
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
    (is (= nil (:tr_loppuosa tallennettava)))
    (is (= nil (:tr_loppuetaisyys tallennettava)))

    ;; Ajolle saatiin muodostettua geometria
    (is (instance? PGgeometry (:sijainti tallennettava)))
    (is (instance? Point (.getGeometry (:sijainti tallennettava))))))

(deftest tarkastus-jossa-kaikki-mittaukset-menee-kantaan-oikein
  (let [tarkastusajo-id 666
        urakka-id (hae-oulun-alueurakan-2014-2019-id)
        merkinnat-tieosoitteilla (lisaa-reittimerkinnoille-mockattu-tieosoite
                                   testidata/tarkastus-jossa-kaikki-mittaukset)
        tarkastukset (reittimerkinnat-tarkastuksiksi (luo-testitietokanta)
                       merkinnat-tieosoitteilla)
        tarkastukset (ls-core/lisaa-tarkastuksille-urakka-id tarkastukset urakka-id)
        tallennettava (luo-kantaan-tallennettava-tarkastus
                        (:db jarjestelma)
                        (first (:reitilliset-tarkastukset tarkastukset))
                        {:kayttajanimi "jvh"})]

    ;; Tallennettavassa mapissa on mittaukset oikein
    (is (= (select-keys (:talvihoitomittaus tallennettava)
                        [:lumimaara :tasaisuus :kitka :lampotila_ilma])
           {:lumimaara 1.0
            :tasaisuus 2.0
            :kitka 3.0
            :lampotila_ilma 4.0}))
    (is (= (select-keys (:soratiemittaus tallennettava)
                        [:tasaisuus :kiinteys :polyavyys :sivukaltevuus])
           {:tasaisuus 1.0
            :kiinteys 2.0
            :polyavyys 3.0
            :sivukaltevuus 4.0}))

    ;; Mittaukset menevät myös kantaan oikein
    (let [_ (jdbc/with-db-transaction [tx (:db jarjestelma)]
              (ls-core/tallenna-muunnetut-tarkastukset-kantaan tx tarkastukset {:id 1} urakka-id))
          tarkastus-kannassa (first (q-map
                                      "SELECT
                                       thm.tasaisuus AS \"talvihoito-tasaisuus\",
                                       thm.lumimaara,
                                       thm.kitka,
                                       thm.lampotila_ilma,
                                       stm.tasaisuus AS \"soratie-tasaisuus\",
                                       stm.kiinteys,
                                       stm.polyavyys,
                                       stm.sivukaltevuus
                                       FROM tarkastus t
                                         LEFT JOIN talvihoitomittaus thm ON thm.tarkastus = t.id
                                         LEFT JOIN soratiemittaus stm ON stm.tarkastus = t.id
                                       WHERE tarkastusajo = " tarkastusajo-id ";"))]
      (is (== (:lumimaara tarkastus-kannassa) 1))
      (is (== (:talvihoito-tasaisuus tarkastus-kannassa) 2))
      (is (== (:kitka tarkastus-kannassa) 3))
      (is (== (:lampotila_ilma tarkastus-kannassa) 4))

      (is (== (:soratie-tasaisuus tarkastus-kannassa) 1))
      (is (== (:kiinteys tarkastus-kannassa) 2))
      (is (== (:polyavyys tarkastus-kannassa) 3))
      (is (== (:sivukaltevuus tarkastus-kannassa) 4))

      ;; Siivoa sotkut
      (u "DELETE FROM tarkastus WHERE tarkastusajo = " tarkastusajo-id ";"))))

(deftest oikean-tarkastusajon-754-muunto-toimii
  (let [db (:db jarjestelma)
        tarkastusajo-id 754
        urakka-id (hae-oulun-alueurakan-2014-2019-id)
        tarkastukset (ls-core/muunna-tarkastusajon-reittipisteet-tarkastuksiksi db tarkastusajo-id)
        tarkastukset (ls-core/lisaa-tarkastuksille-urakka-id tarkastukset urakka-id)
        reitilliset (:reitilliset-tarkastukset tarkastukset)
        pistemaiset (:pistemaiset-tarkastukset tarkastukset)
        odotettu-pistemaisten-maara 0
        odotettu-reitillisten-maara 4
        kaikki-tarkastukset (concat reitilliset pistemaiset)
        odotetut-tarkastetut-tieosat
        [{:tie 18637 :aosa 1 :aet 207 :losa 1 :let 187}
         {:tie 18637 :aosa 1 :aet 187 :losa 1 :let 11}
         {:tie 28409 :aosa 23 :aet 20 :losa 23 :let 401}
         {:tie 4 :aosa 364 :aet 3586 :losa 367 :let 335}]]

    ;; Muunnettu määrällisesti oikein
    (is (= (count pistemaiset) odotettu-pistemaisten-maara))
    (is (= (count reitilliset) odotettu-reitillisten-maara))

    ;; Kaikissa pisteissä on tietyt kentät oikein
    (is (every? #(= (:urakka %) urakka-id) kaikki-tarkastukset))
    (is (every? #(= (:tarkastusajo %) tarkastusajo-id) kaikki-tarkastukset))
    (is (every? #(some? (:aika %)) kaikki-tarkastukset))
    (is (every? #(= (:laadunalitus %) false) kaikki-tarkastukset))
    (is (every? #(empty? (:liitteet %)) kaikki-tarkastukset))

    ;; Jokainen tallennettava tarkastus muodostetaan tieosoitteen osalta tarkalleen oikein
    (tarkista-tallennettujen-tarkastuksien-osoite kaikki-tarkastukset odotetut-tarkastetut-tieosat)

    (let [tarkastusten-maara-ennen (ffirst (q "SELECT COUNT(*) FROM tarkastus"))
          _ (jdbc/with-db-transaction [tx db]
              (ls-core/tallenna-muunnetut-tarkastukset-kantaan tx tarkastukset +kayttaja-jvh+ urakka-id))
          tarkastusten-maara-jalkeen (ffirst (q "SELECT COUNT(*) FROM tarkastus"))
          tarkastukset-kannassa (q-map "SELECT * FROM tarkastus WHERE tarkastusajo = " tarkastusajo-id ";")]

      ;; Määrä lisääntyi oikein
      (is (= (+ tarkastusten-maara-ennen odotettu-reitillisten-maara odotettu-pistemaisten-maara)
             tarkastusten-maara-jalkeen))

      ;; Tiedot kirjautuivat kantaan täsmällisesti oikein
      (is (every? #(= (:urakka %) urakka-id) tarkastukset-kannassa))
      (is (every? #(= (:tarkastusajo %) tarkastusajo-id) tarkastukset-kannassa))
      (is (every? #(some? (:aika %)) tarkastukset-kannassa))
      (is (every? #(some? (:luotu %)) tarkastukset-kannassa))
      (is (every? #(some? (:luoja %)) tarkastukset-kannassa))
      (is (every? #(= (:poistettu %) false) tarkastukset-kannassa))
      (is (every? #(nil? (:muokattu %)) tarkastukset-kannassa))
      (is (every? #(= (:lahde %) "harja-ls-mobiili") tarkastukset-kannassa))
      (is (every? #(= (:tyyppi %) "laatu") tarkastukset-kannassa))
      (is (every? #(= (:laadunalitus %) false) tarkastukset-kannassa))
      (is (every? #(= (:nayta_urakoitsijalle %) false) tarkastukset-kannassa))
      (is (every? #(nil? (:havainnot %)) tarkastukset-kannassa))
      ;; Myös tie menee oikein
      (loop [i 0]
        (is (= (-> (nth tarkastukset-kannassa i)
                   (select-keys
                     [:tr_numero :tr_alkuosa :tr_alkuetaisyys :tr_loppuosa :tr_loppuetaisyys])
                   (set/rename-keys {:tr_numero :tie
                                     :tr_alkuosa :aosa
                                     :tr_alkuetaisyys :aet
                                     :tr_loppuosa :losa
                                     :tr_loppuetaisyys :let})))
            (nth odotetut-tarkastetut-tieosat i))

        (when (< i (- (count odotetut-tarkastetut-tieosat) 1))
          (recur (inc i))))
      ;; Ja geometria
      (is (every? #(instance? MultiLineString (.getGeometry (:sijainti %))) tarkastukset-kannassa))

      ;; Siivoa sotkut
      (u "DELETE FROM tarkastus WHERE tarkastusajo = " tarkastusajo-id ";"))))

(deftest oikean-tarkastusajon-3-muunto-toimii
  (let [db (:db jarjestelma)
        tarkastusajo-id 3
        urakka-id (hae-oulun-alueurakan-2014-2019-id)
        tarkastukset (ls-core/muunna-tarkastusajon-reittipisteet-tarkastuksiksi db tarkastusajo-id)
        tarkastukset (ls-core/lisaa-tarkastuksille-urakka-id tarkastukset urakka-id)
        reitilliset (:reitilliset-tarkastukset tarkastukset)
        pistemaiset (:pistemaiset-tarkastukset tarkastukset)
        odotettu-pistemaisten-maara 0
        odotettu-reitillisten-maara 7]

    ;; Tämä testi havaitsi aiemmin virheellisiä ajallisia gäppejä tästä ajosta.
    ;; Tämä testi vaatii, että piste numero 36, joka osuu virheellisesti eri tielle risteyksessä,
    ;; saadaan projisoitua takaisin edelliselle tielle

    ;; Muunnettu määrällisesti oikein
    (is (= (count pistemaiset) odotettu-pistemaisten-maara))
    (is (= (count reitilliset) odotettu-reitillisten-maara))))

(deftest oikean-tarkastusajon-213-muunto-toimii
  "Ajo lähtee tieverkon ulkopuolelta ja päätyy tieverkolle"
  (let [db (:db jarjestelma)
        tarkastusajo-id 213
        urakka-id (hae-oulun-alueurakan-2014-2019-id)
        tarkastukset (ls-core/muunna-tarkastusajon-reittipisteet-tarkastuksiksi db tarkastusajo-id)
        tarkastukset (ls-core/lisaa-tarkastuksille-urakka-id tarkastukset urakka-id)
        reitilliset (:reitilliset-tarkastukset tarkastukset)
        pistemaiset (:pistemaiset-tarkastukset tarkastukset)
        odotettu-pistemaisten-maara 0
        odotettu-reitillisten-maara 4
        osa1 (nth reitilliset 0)
        osa2 (nth reitilliset 1)
        osa3 (nth reitilliset 2)
        osa4 (nth reitilliset 3)]

    ;; Tässä pitäisi muodostua 4 tarkastusta:
    ;; 1. Ajo lähtee tieverkon ulkopuolelta ja katkeaa kun laitetaan jatkuva havainto päälle.
    ;;    Tarkastus on kokonaisuudessaan tieverkon ulkopuolella, joten
    ;;    tälle tarkastukselle ei saada muodostettua tieosoitetta. Tämä on OK.
    ;; 2. Ajo, jossa on jatkuva havainto päällä. Jossain vaiheessa tullaan tieverkolle.
    ;;    Reitti katkaistaan tästä ja uusi tarkastus alkaa pisteestä, jossa tie on messissä.
    ;; 3. Jatkuva havainto päällä, ajetaan tieverkolla.
    ;; 4. Jatkuva havainto laitettu pois päältä. Ajetaan tieverkolla ilman havaintoja.

    ;; Muunnettu määrällisesti oikein
    (is (= (count pistemaiset) odotettu-pistemaisten-maara))
    (is (= (count reitilliset) odotettu-reitillisten-maara))

    ;; Muunnosten sisältö vastaa yllä kuvattua olettamusta
    (is (= (:lopullinen-tr-osoite osa1) {:tie nil, :aosa nil, :aet nil, :losa nil, :let nil}))
    (is (empty? (:vakiohavainnot osa1)))
    (is (= (:lopullinen-tr-osoite osa2) {:tie nil, :aosa nil, :aet nil, :losa nil, :let nil}))
    (is (not (empty? (:vakiohavainnot osa2))))
    (is (= (:lopullinen-tr-osoite osa3) {:tie 18637, :aosa 1, :aet 1237, :losa 1, :let 1190}))
    (is (not (empty? (:vakiohavainnot osa2))))
    (is (= (:lopullinen-tr-osoite osa4) {:tie 18637, :aosa 1, :aet 1190, :losa 1, :let 1139}))
    (is (empty? (:vakiohavainnot osa4)))))

;; -------- Apufunktioita REPL-tunkkaukseen --------

;; HUOM! Älä poista näitä. Näitä käytetään tarkastusajojen debuggaamiseen
;; ja ongelmien selvittämiseen.
;;
;; Kutsu tässä NS:ssä esim. (debuggaa-tarkastusajon-muunto (:db harja.palvelin.main/harja-jarjestelma) 425)
;; Muista evaluoida REPLiin ensin (käännös ei sisällä näitä koska näitä ei käytetä)

;; Toinen kätevä debuggausapuri on "salainen" TR-osio, jossa on mahdollista
;; piirtää tarkastusajon kaikki raakamerkinnät kartalle:
;; http://localhost:3000/#tr

(defn muunna-tarkastusajo-kantaan
  "Muuntaa annetun tarkastusajon kantaan.

  Jos tarkoituksena on korjata tuotannossa virheellisesti muunnettu tarkastusajo,
  niin kätevimmin se tapahtuu seuraavasti:
  1) Kopioi tarkastusajo ja sen kaikki yksittäiset merkinnät omaan kantaan.
  2) Tee muunnos tällä funktiolla.
  3) Poista tuotannosta virheelliset tarkastukset ja kopioi tilalle uudet,
     korjatut tarkastukset.

  HOX! Tämä EI poista mahdollisesti jo kerran tehtyä muunnosta!"
  [db tarkastusajo-id urakka-id]
  (log/debug "Muunnetaan tarkastusajo " (pr-str tarkastusajo-id) " kantaan urakkaan " urakka-id)
  (let [tarkastukset (ls-core/muunna-tarkastusajon-reittipisteet-tarkastuksiksi db tarkastusajo-id)
        tarkastukset (ls-core/lisaa-tarkastuksille-urakka-id tarkastukset urakka-id)]
    (jdbc/with-db-transaction [tx db]
      (ls-core/tallenna-muunnetut-tarkastukset-kantaan tx tarkastukset 1 urakka-id))))

(defn debuggaa-tarkastusajon-muunto
  "Ottaa tarkastusajon id:n ja tulostaa kattavan login siitä, miten
   se muunnetaan Harjaan yksittäisiksi tarkastuksiksi.

   Tätä voidaan käyttää apuna selvittämään syytä sille, miksi jokin
   tarkastusajo on mahdollisesti muunnettu väärin.

   Muista ajaa tieverkko kantaan, jotta geometrisointi toimii!"
  [db tarkastusajo-id]
  (log/debug "Debugataan tarkastusajo: " (pr-str tarkastusajo-id))
  (let [tarkastukset (ls-core/muunna-tarkastusajon-reittipisteet-tarkastuksiksi db tarkastusajo-id)
        tie->str (fn [tie]
                   (str "Tie " (or (:tie tie)
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
    (let [sijainnit (mapcat :sijainnit (sort-by :aika kaikki-tarkastukset))]
      (doseq [sijainti sijainnit]
        (log/debug (str (:sijainti sijainti) " --> " (tie->str (:tr-osoite sijainti))
                        " (ramppi? " (tr-domain/tie-rampilla? (get-in sijainti [:tr-osoite :tie])) ")"))))

    (log/debug "")
    (log/debug "-- Lopputulos --")
    (log/debug "Lopulliset Harjan kantaan menevät tarkastusten osoitteet:")
    (doseq [tarkastus kaikki-tarkastukset]
      (let [tallennettava (luo-kantaan-tallennettava-tarkastus
                            db
                            tarkastus
                            {:kayttajanimi "jvh"})]
        (log/debug (tie->str tallennettava))))))

(defn debuggaa-tarkastusajojen-muunto
  [db tarkastusajo-idt]
  (log/debug "Debugataan tarkastusajot: " (pr-str tarkastusajo-idt))
  (doseq [tarkastusajo-id tarkastusajo-idt]
    (debuggaa-tarkastusajon-muunto db tarkastusajo-id)))
