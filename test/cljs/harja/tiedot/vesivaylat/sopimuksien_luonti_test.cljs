(ns harja.tiedot.vesivaylat.sopimuksien-luonti-test
  (:require [harja.tiedot.vesivaylat.sopimuksien-luonti :as s]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [tuck.core :as tuck]))

(def tila @s/tila)

(deftest sopimuksen-valinta
  (let [sopimus {:foobar 1}]
    (is (= sopimus (:valittu-sopimus (e! tila s/->ValitseSopimus sopimus))))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! tila s/->Nakymassa? true))))
  (is (false? (:nakymassa? (e! tila s/->Nakymassa? false)))))

(deftest uuden-sopimuksen-luonnin-aloitus
  (is (= s/uusi-sopimus (:valittu-sopimus (e! tila s/->UusiSopimus)))))

(deftest tallentamisen-aloitus
  (vaadi-async-kutsut
    #{s/->SopimusTallennettu s/->SopimusEiTallennettu}

    (is (true? (:tallennus-kaynnissa? (e! {:haetut-sopimukset []} s/->TallennaSopimus {:id 1}))))))

(deftest tallentamisen-valmistuminen
  (testing "Uuden sopimuksen tallentaminen"
    (let [vanhat [{:id 1} {:id 2}]
          uusi {:id 3}
          tulos (e! {:haetut-sopimukset vanhat} s/->SopimusTallennettu uusi)]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-sopimus tulos)))
      (is (= (conj vanhat uusi) (:haetut-sopimukset tulos)))))

  (testing "Sopimuksen muokkaaminen"
    (let [vanhat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
          uusi {:id 2 :nimi :bb}
          tulos (e! {:haetut-sopimukset vanhat} s/->SopimusTallennettu uusi)]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-sopimus tulos)))
      (is (= [{:id 1 :nimi :a} {:id 2 :nimi :bb}] (:haetut-sopimukset tulos))))))

(deftest tallentamisen-epaonnistuminen
  (let [tulos (e! tila s/->SopimusEiTallennettu "virhe")]
    (is (false? (:tallennus-kaynnissa? tulos)))
    (is (nil? (:valittu-sopimus tulos)))))

(deftest sopimuksen-muokkaaminen-lomakkeessa
  (let [sopimus {:nimi :foobar}]
    (is (= sopimus (:valittu-sopimus (e! tila s/->SopimustaMuokattu sopimus))))))

(deftest hakemisen-aloitus
  (vaadi-async-kutsut
    #{s/->SopimuksetHaettu s/->SopimuksetEiHaettu}

    (is (true? (:sopimuksien-haku-kaynnissa? (e! tila s/->HaeSopimukset))))))

(deftest hakemisen-valmistuminen
  (let [tulos (e! {:haetut-sopimukset []} s/->SopimuksetHaettu [{:id 1}])]
    (is (false? (:sopimuksien-haku-kaynnissa? tulos)))
    (is (= [{:id 1}] (:haetut-sopimukset tulos)))))

(deftest hakemisen-epaonnistuminen
  (let [tulos (e! tila s/->SopimuksetEiHaettu "virhe")]
    (is (false? (:sopimuksien-haku-kaynnissa? tulos)))))


