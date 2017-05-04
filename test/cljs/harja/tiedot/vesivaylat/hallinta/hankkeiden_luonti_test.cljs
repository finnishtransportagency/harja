(ns harja.tiedot.vesivaylat.hallinta.hankkeiden-luonti-test
  (:require [harja.tiedot.vesivaylat.hallinta.hankkeiden-luonti :as h]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e-tila! e!]]))

(deftest hankkeen-valinta
  (let [hanke {:foobar 1}]
    (is (= hanke (:valittu-hanke (e! h/->ValitseHanke hanke))))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! h/->Nakymassa? true))))
  (is (false? (:nakymassa? (e! h/->Nakymassa? false)))))

(deftest uuden-hankkeen-luonnin-aloitus
  (is (= h/uusi-hanke (:valittu-hanke (e! h/->UusiHanke)))))

(deftest tallentamisen-aloitus
  (vaadi-async-kutsut
    #{h/->HankeTallennettu h/->HankeEiTallennettu}

    (is (true? (:tallennus-kaynnissa? (e-tila! h/->TallennaHanke {:id 1} {:haetut-hankkeet []}))))))

(deftest tallentamisen-valmistuminen
  (testing "Uuden hankkeen tallentaminen"
    (let [vanhat [{:id 1} {:id 2}]
          uusi {:id 3}
          tulos (e-tila! h/->HankeTallennettu uusi {:haetut-hankkeet vanhat})]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-hanke tulos)))
      (is (= (conj vanhat uusi) (:haetut-hankkeet tulos)))))

  (testing "Hankkeen muokkaaminen"
    (let [vanhat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
          uusi {:id 2 :nimi :bb}
          tulos (e-tila! h/->HankeTallennettu uusi {:haetut-hankkeet vanhat})]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-hanke tulos)))
      (is (= [{:id 1 :nimi :a} {:id 2 :nimi :bb}] (:haetut-hankkeet tulos))))))

(deftest tallentamisen-epaonnistuminen
  (let [tulos (e! h/->HankeEiTallennettu "virhe")]
    (is (false? (:tallennus-kaynnissa? tulos)))
    (is (nil? (:valittu-hanke tulos)))))

(deftest hankkeen-muokkaaminen-lomakkeessa
  (let [hanke {:nimi :foobar}]
    (is (= hanke (:valittu-hanke (e! h/->HankettaMuokattu hanke))))))

(deftest hakemisen-aloitus
  (vaadi-async-kutsut
    #{h/->HankkeetHaettu h/->HankkeetEiHaettu}

    (is (true? (:hankkeiden-haku-kaynnissa? (e! h/->HaeHankkeet))))))

(deftest hakemisen-valmistuminen
  (let [tulos (e-tila! h/->HankkeetHaettu [{:id 1}] {:haetut-hankkeet []})]
    (is (false? (:hankkeiden-haku-kaynnissa? tulos)))
    (is (= [{:id 1}] (:haetut-hankkeet tulos)))))

(deftest hakemisen-epaonnistuminen
  (let [tulos (e! h/->HankkeetEiHaettu "virhe")]
    (is (false? (:hankkeiden-haku-kaynnissa? tulos)))))
