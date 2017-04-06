(ns harja.tiedot.vesivaylat.urakoitsijan-luonti-test
  (:require [harja.tiedot.vesivaylat.urakoitsijan-luonti :as u]
            [clojure.test :refer-macros [deftest is testing]]
            [tuck.core :as tuck]))

(def tila @u/tila)

(defn e!
  [event & payload]
  (tuck/process-event (apply event payload) tila))

(deftest urakoitsijan-valinta
  (let [urakoitsija {:foobar 1}]
    (is (= urakoitsija (:valittu-urakoitsija (e! u/->ValitseUrakoitsija urakoitsija))))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! u/->Nakymassa? true))))
  (is (false? (:nakymassa? (e! u/->Nakymassa? false)))))

(deftest uuden-urakoitsijan-luonnin-aloitus
  (is (= u/uusi-urakoitsija (:valittu-urakoitsija (e! u/->UusiUrakoitsija)))))

(deftest tallentamisen-aloitus
  (let [halutut #{u/->UrakoitsijaTallennettu u/->UrakoitsijaEiTallennettu}
        kutsutut (atom #{})]
    (with-redefs
      [tuck/send-async! (fn [r & _] (swap! kutsutut conj r))
       ;; Haetut on oletuksena tyhjä, mutta tallentamista ei voi tehdä jos näin on
       tila {:haetut-urakoitsijat []}]
      (is (true? (:tallennus-kaynnissa? (e! u/->TallennaUrakoitsija {:id 1}))))
      (is (= halutut @kutsutut)))))

(deftest tallentamisen-valmistuminen
  (testing "Uuden urakoitsijan tallentaminen"
    (let [vanhat [{:id 1} {:id 2}]
          uusi {:id 3}
          tulos (with-redefs [tila {:haetut-urakoitsijat vanhat}] (e! u/->UrakoitsijaTallennettu uusi))]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakoitsija tulos)))
      (is (= (conj vanhat uusi) (:haetut-urakoitsijat tulos)))))

  (testing "Urakoitsijan muokkaaminen"
    (let [vanhat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
          uusi {:id 2 :nimi :bb}
          tulos (with-redefs [tila {:haetut-urakoitsijat vanhat}] (e! u/->UrakoitsijaTallennettu uusi))]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakoitsija tulos)))
      (is (= [{:id 1 :nimi :a} {:id 2 :nimi :bb}] (:haetut-urakoitsijat tulos))))))

(deftest tallentamisen-epaonnistuminen
  (let [tulos (e! u/->UrakoitsijaEiTallennettu "virhe")]
    (is (false? (:tallennus-kaynnissa? tulos)))
    (is (nil? (:valittu-urakoitsija tulos)))))

(deftest urakoitsijan-muokkaaminen-lomakkeessa
  (let [urakoitsija {:nimi :foobar}]
    (is (= urakoitsija (:valittu-urakoitsija (e! u/->UrakoitsijaaMuokattu urakoitsija))))))

