(ns harja-laadunseuranta.tarkastukset-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [harja-laadunseuranta.tarkastukset :refer [reittimerkinnat-tarkastuksiksi luo-tallennettava-tarkastus]]
            [harja-laadunseuranta.testidata :as testidata]
            [taoensso.timbre :as log]
            [harja-laadunseuranta.tarkastukset :as tarkastukset]))

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

(deftest reittimerkinnat-tarkastuksiksi-kommentit-keraytyvat
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastukset-joissa-jatkuvat-havainnot-muuttuu-ja-kommentteja))]
    (testing "Kommentit kerääntyvät oikein"
      (is (= (-> tarkastukset :pistemaiset-tarkastukset) []))
      (is (= (-> tarkastukset :reitilliset-tarkastukset (get 2) :havainnot) nil)))))

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
    (is (= (-> tarkastukset :pistemaiset-tarkastukset first :talvihoitomittaus :kitka)  0.2))

    ;; Havainnot lisätty oikein
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :vakiohavainnot)
           [17]))
    (is (every? #{17 20} (-> tarkastukset :pistemaiset-tarkastukset first :vakiohavainnot)))))

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
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :tasaisuus) 2))))

(deftest soratie-kiinteys-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-kiinteys))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :kiinteys) 3))))

(deftest soratie-polyavyys-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-polyavyys))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :polyavyys) 2))))

(deftest soratie-sivukaltevuus-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-soratie-sivukaltevuus))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Tasaisuus laskettu oikein
    (is (== (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus :sivukaltevuus) 3))))

(deftest kaikki-reittimerkinnat-tarkastuksiksi
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/monipuolinen-tarkastus))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 5))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 3))

    ;; Liitteet lisätty oikein
    (is (= (-> tarkastukset :reitilliset-tarkastukset (get 1) :liite) 1))
    (is (= (-> tarkastukset :pistemaiset-tarkastukset last :liite) 1))))

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

(deftest tarkastus-trvali-jossa-alkuosa-vaihtuu
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-alkuosa-vaihtuu))
        tallennettava (luo-tallennettava-tarkastus
                        (first (:reitilliset-tarkastukset tarkastukset))
                        {:kayttajanimi "jvh"})]
    (is (= 1 (count (:reitilliset-tarkastukset tarkastukset))))
    (is (= 20 (:tr_numero tallennettava)))
    (is (= 10 (:tr_alkuosa tallennettava)))
    (is (= 4924 (:tr_alkuetaisyys tallennettava)))
    (is (= 11 (:tr_loppuosa tallennettava)))
    (is (= 6349 (:tr_loppuetaisyys tallennettava)))))

(deftest tarkastus-jossa-jatkuva-laadunalitus
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-jatkuva-laadunalitus))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Koko tarkastus on merkitty laadunalitukseksi, koska sellainen löytyi osasta tarkastuspisteitä
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :laadunalitus) true))))

(deftest tarkastus-jossa-liittyva-havainto
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi
                       (lisaa-reittimerkinnoille-mockattu-tieosoite
                         testidata/tarkastus-jossa-liittyvia-havaintoja))]
    ;; Yksi pistemäinen havainto, johon liitetty lisätietoja
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 1))
    (is (= (:kuvaus (first (:pistemaiset-tarkastukset tarkastukset)))
           "Tässä on nyt jotain mätää\nTässä vielä toinen kuva"))
    (is (= (:liite (first (:pistemaiset-tarkastukset tarkastukset)))
           [1 2]))
    ;; Muu osa (ei pistemäinen havainto eikö siihen liittyvät merkinnät) on yksi jatkuva havainto
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))))
