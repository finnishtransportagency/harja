(ns harja.tiedot.vesivaylat.hankkeiden-luonti-test
  (:require [harja.tiedot.vesivaylat.hankkeiden-luonti :as h]
            [clojure.test :refer-macros [deftest is testing]]
            [tuck.core :as tuck]))

(def tila @h/tila)

(defn e!
  [event & payload]
  (tuck/process-event (apply event payload) tila))

(deftest hankkeen-valinta
  (let [hanke {:foobar 1}]
    (is (= hanke (:valittu-hanke (e! h/->ValitseHanke hanke))))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! h/->Nakymassa? true))))
  (is (false? (:nakymassa? (e! h/->Nakymassa? false)))))

(deftest uuden-hankkeen-luonnin-aloitus
  (is (= h/uusi-hanke (:valittu-hanke (e! h/->UusiHanke)))))

(deftest tallentamisen-aloitus
  (let [halutut #{h/->HankeTallennettu h/->HankeEiTallennettu}
        kutsutut (atom #{})]
    (with-redefs
      [tuck/send-async! (fn [r & _] (swap! kutsutut conj r))
       ;; Haetut on oletuksena tyhjä, mutta tallentamista ei voi tehdä jos näin on
       tila {:haetut-hankkeet []}]
      (is (true? (:tallennus-kaynnissa? (e! h/->TallennaHanke {:id 1}))))
      (is (= halutut @kutsutut)))))

(deftest tallentamisen-valmistuminen
  (testing "Uuden hankkeen tallentaminen"
    (let [vanhat [{:id 1} {:id 2}]
          uusi {:id 3}
          tulos (with-redefs [tila {:haetut-hankkeet vanhat}] (e! h/->HankeTallennettu uusi))]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-hanke tulos)))
      (is (= (conj vanhat uusi) (:haetut-hankkeet tulos)))))

  (testing "Hankkeen muokkaaminen"
    (let [vanhat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
          uusi {:id 2 :nimi :bb}
          tulos (with-redefs [tila {:haetut-hankkeet vanhat}] (e! h/->HankeTallennettu uusi))]
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
