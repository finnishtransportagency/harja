(ns harja-laadunseuranta.tarkastukset-test
  (:require [clojure.test :as t :refer [deftest testing is]]
            [harja-laadunseuranta.tarkastukset :refer [reittimerkinnat-tarkastuksiksi luo-tallennettava-tarkastus]]
            [harja-laadunseuranta.testidata :as testidata]
            [taoensso.timbre :as log]
            [harja-laadunseuranta.tarkastukset :as tarkastukset]))

(defn lisaa-reittimerkinnoille-mockattu-tieosoite
  "Mock-funktio, joka lisää tiemerkinnöille tierekisteriosoitteet ilman oikeaa kannassa olevaa tieverkkoa.
  Mockin lisäämät tierekisteriarvot pohjautuvat kuitenkin oikeisiin tierekisteriosoitteisiin (ainakin tätä kirjoittaessa)"
  [reittimerkinnat]
  (map (fn [reittimerkinta]
         (let [tierekisteriosoite (get testidata/mockattu-tierekisteri (:sijainti reittimerkinta))]
           (if tierekisteriosoite
             (assoc reittimerkinta :tr-osoite tierekisteriosoite)
             reittimerkinta)))
       reittimerkinnat))

(deftest reittimerkinnat-tarkastuksiksi-havainnot-muuttuu
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/tarkastukset-joissa-jatkuvat-havainnot-muuttuu))]
    ;; Munnetaan määrällisesti okein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 3))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))

    ;; Havainnot lisätty oikein
    (is (empty? (-> tarkastukset :reitilliset-tarkastukset first :vakiohavainnot)))
    (is (= (-> tarkastukset :reitilliset-tarkastukset second :vakiohavainnot)
           [17]))
    (is (empty? (-> tarkastukset :reitilliset-tarkastukset (get 2) :vakiohavainnot)))))

(deftest reittimerkinnat-tarkastuksiksi-kitka-laskettu-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/tarkastukset-joissa-jatkuvat-havainnot-muuttuu))]
    ;; Kitkamäärät laskettu oikein
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :talvihoitomittaus :kitka) nil))
    (is (= (-> tarkastukset :reitilliset-tarkastukset second :talvihoitomittaus :kitka) 0.25))
    (is (= (-> tarkastukset :reitilliset-tarkastukset (get 2) :talvihoitomittaus :kitka) nil))))

(deftest reittimerkinnat-tarkastuksiksi-kommentit-keraytyvat
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/tarkastukset-joissa-jatkuvat-havainnot-muuttuu-ja-kommentteja))]
    (testing "Kommentit kerääntyvät oikein"
      (is (= (-> tarkastukset :pistemaiset-tarkastukset) []))
      (is (= (-> tarkastukset :reitilliset-tarkastukset second :havainnot) "foo\nbar"))
      (is (= (-> tarkastukset :reitilliset-tarkastukset (get 2) :havainnot) nil)))))

(deftest soratietarkastus-menee-oikein
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/soratiehavainnon-mittaukset))]
    (is (= (-> tarkastukset :pistemaiset-tarkastukset) [])) 
    (is (= (-> tarkastukset :reitilliset-tarkastukset first :soratiemittaus) {:hoitoluokka nil
                                                                              :tasaisuus 3
                                                                              :kiinteys 4
                                                                              :polyavyys 5
                                                                              :sivukaltevuus nil}))))

(deftest pistemaiset-reittimerkinnat-tarkastuksiksi
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/tarkastus-jossa-pistemainen-havainto))]
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

(deftest kaikki-reittimerkinnat-tarkastuksiksi
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/monipuolinen-tarkastus))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 5))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 3))

    ;; Liitteet lisätty oikein
    (is (= (-> tarkastukset :reitilliset-tarkastukset (get 1) :liite) 1))
    (is (= (-> tarkastukset :pistemaiset-tarkastukset last :liite) 1))))

(deftest tarkastus-jossa-piste-ei-osu-tielle
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/tarkastus-jossa-piste-ei-osu-tielle))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 1))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-jossa-tie-vaihtuu
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/tarkastus-jossa-tie-vaihtuu))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

#_(deftest tarkastus-jossa-kaannytaan-ympari
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/tarkastus-jossa-kaannytaan-ympari))]
    ;; Muunnettu määrällisesti oikein
    (is (= (count (:reitilliset-tarkastukset tarkastukset)) 2))
    (is (= (count (:pistemaiset-tarkastukset tarkastukset)) 0))))

(deftest tarkastus-trvali-jossa-alkuosa-vaihtuu
  (let [tarkastukset (reittimerkinnat-tarkastuksiksi (lisaa-reittimerkinnoille-mockattu-tieosoite testidata/tarkastus-jossa-alkuosa-vaihtuu))
        tallennettava (luo-tallennettava-tarkastus (first (:reitilliset-tarkastukset tarkastukset)) {:kayttajanimi "jvh"})]
    (is (= 1 (count (:reitilliset-tarkastukset tarkastukset))))
    (is (= 20 (:tr_numero tallennettava)))
    (is (= 10 (:tr_alkuosa tallennettava)))
    (is (= 4924 (:tr_alkuetaisyys tallennettava)))
    (is (= 11 (:tr_loppuosa tallennettava)))
    (is (= 6349 (:tr_loppuetaisyys tallennettava)))))
