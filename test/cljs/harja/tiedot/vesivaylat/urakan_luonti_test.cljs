(ns harja.tiedot.vesivaylat.urakan-luonti-test
  (:require [harja.tiedot.vesivaylat.urakan-luonti :as u]
            [clojure.test :refer-macros [deftest is testing]]
            [tuck.core :as tuck]))

(def tila @u/tila)

(defn e!
  [event & payload]
  (tuck/process-event (apply event payload) tila))

(deftest urakan-valinta
  (let [ur {:foobar 1}]
    (is (= ur (:valittu-urakka (e! u/->ValitseUrakka ur))))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! u/->Nakymassa? true))))
  (is (false? (:nakymassa? (e! u/->Nakymassa? false)))))

(deftest uuden-urakan-luonnin-aloitus
  (is (= u/uusi-urakka (:valittu-urakka (e! u/->UusiUrakka)))))

(deftest tallentamisen-aloitus
  (let [halutut #{u/->UrakkaTallennettu u/->UrakkaEiTallennettu}
        kutsutut (atom #{})]
    (with-redefs
      [tuck/send-async! (fn [r & _] (swap! kutsutut conj r))]
      (is (true? (:tallennus-kaynnissa? (e! u/->TallennaUrakka {:id 1}))))
      (is (= halutut @kutsutut)))))

(deftest tallentamisen-valmistuminen
  (testing "Uuden urakan tallentaminen"
    (let [vanhat [{:id 1} {:id 2}]
          uusi {:id 3}
          tulos (with-redefs [tila {:haetut-urakat vanhat}] (e! u/->UrakkaTallennettu uusi))]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakka tulos)))
      (is (= (conj vanhat uusi) (:haetut-urakat tulos)))))

  (testing "Urakan muokkaaminen"
    (let [vanhat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
          uusi {:id 2 :nimi :bb}
          tulos (with-redefs [tila {:haetut-urakat vanhat}] (e! u/->UrakkaTallennettu uusi))]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakka tulos)))
      (is (= [{:id 1 :nimi :a} {:id 2 :nimi :bb}] (:haetut-urakat tulos))))))

(deftest tallentamisen-epaonnistuminen
  (let [tulos (e! u/->UrakkaEiTallennettu "virhe")]
    (is (false? (:tallennus-kaynnissa? tulos)))
    (is (nil? (:valittu-urakka tulos)))))

(deftest urakan-muokkaaminen-lomakkeessa
  (let [ur {:nimi :foobar}]
    (is (= ur (:valittu-urakka (e! u/->UrakkaaMuokattu ur))))))