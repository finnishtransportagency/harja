(ns harja.tiedot.vesivaylat.urakoitsijoiden-luonti-test
  (:require [harja.tiedot.vesivaylat.urakoitsijoiden-luonti :as u]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [tuck.core :as tuck]))

(def tila @u/tila)


(deftest urakoitsijan-valinta
  (let [urakoitsija {:foobar 1}]
    (is (= urakoitsija (:valittu-urakoitsija (e! tila u/->ValitseUrakoitsija urakoitsija))))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! tila u/->Nakymassa? true))))
  (is (false? (:nakymassa? (e! tila u/->Nakymassa? false)))))

(deftest uuden-urakoitsijan-luonnin-aloitus
  (is (= u/uusi-urakoitsija (:valittu-urakoitsija (e! tila u/->UusiUrakoitsija)))))

(deftest tallentamisen-aloitus
  (vaadi-async-kutsut
    #{u/->UrakoitsijaTallennettu u/->UrakoitsijaEiTallennettu}

    (is (true? (:tallennus-kaynnissa? (e! {:haetut-urakoitsijat []} u/->TallennaUrakoitsija  {:id 1}))))))

(deftest tallentamisen-valmistuminen
  (testing "Uuden urakoitsijan tallentaminen"
    (let [vanhat [{:id 1} {:id 2}]
          uusi {:id 3}
          tulos (e! {:haetut-urakoitsijat vanhat}  u/->UrakoitsijaTallennettu  uusi)]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakoitsija tulos)))
      (is (= (conj vanhat uusi) (:haetut-urakoitsijat tulos)))))

  (testing "Urakoitsijan muokkaaminen"
    (let [vanhat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
          uusi {:id 2 :nimi :bb}
          tulos (e! {:haetut-urakoitsijat vanhat} u/->UrakoitsijaTallennettu  uusi)]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakoitsija tulos)))
      (is (= [{:id 1 :nimi :a} {:id 2 :nimi :bb}] (:haetut-urakoitsijat tulos))))))

(deftest tallentamisen-epaonnistuminen
  (let [tulos (e! tila u/->UrakoitsijaEiTallennettu "virhe")]
    (is (false? (:tallennus-kaynnissa? tulos)))
    (is (nil? (:valittu-urakoitsija tulos)))))

(deftest urakoitsijan-muokkaaminen-lomakkeessa
  (let [urakoitsija {:nimi :foobar}]
    (is (= urakoitsija (:valittu-urakoitsija (e! tila u/->UrakoitsijaaMuokattu urakoitsija))))))

(deftest hakemisen-aloitus
  (vaadi-async-kutsut
    #{u/->UrakoitsijatHaettu u/->UrakoitsijatEiHaettu}

    (is (true? (:urakoitsijoiden-haku-kaynnissa? (e! tila u/->HaeUrakoitsijat {:id 1}))))))

(deftest hakemisen-valmistuminen
  (let [urakat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
        tulos (e! tila u/->UrakoitsijatHaettu urakat)]
    (is (false? (:urakoitsijoiden-haku-kaynnissa? tulos)))
    (is (= [{:id 1 :nimi :a} {:id 2 :nimi :b}] (:haetut-urakoitsijat tulos)))))

(deftest hakemisen-epaonnistuminen
  (let [tulos (e! tila u/->UrakoitsijatEiHaettu "virhe")]
    (is (false? (:urakoitsijoiden-haku-kaynnissa? tulos)))))

