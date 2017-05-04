(ns harja.tiedot.vesivaylat.hallinta.urakoitsijoiden-luonti-test
  (:require [harja.tiedot.vesivaylat.hallinta.urakoitsijoiden-luonti :as tiedot]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.domain.urakka :as u]
            [harja.domain.organisaatio :as o]
            [harja.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e!]]
            [harja.pvm :as pvm]))

(deftest urakoitsijan-valinta
  (let [urakoitsija {:foobar 1}]
    (is (= urakoitsija (:valittu-urakoitsija (e! (tiedot/->ValitseUrakoitsija urakoitsija)))))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! (tiedot/->Nakymassa? true)))))
  (is (false? (:nakymassa? (e! (tiedot/->Nakymassa? false))))))

(deftest uuden-urakoitsijan-luonnin-aloitus
  (is (= tiedot/uusi-urakoitsija (:valittu-urakoitsija (e! (tiedot/->UusiUrakoitsija))))))

(deftest tallentamisen-aloitus
  (vaadi-async-kutsut
    #{tiedot/->UrakoitsijaTallennettu tiedot/->UrakoitsijaEiTallennettu}

    (is (true? (:tallennus-kaynnissa? (e! (tiedot/->TallennaUrakoitsija {:id 1}) {:haetut-urakoitsijat []}))))))

(deftest tallentamisen-valmistuminen
  (testing "Uuden urakoitsijan tallentaminen"
    (let [vanhat [{:id 1} {:id 2}]
          uusi {:id 3}
          tulos (e! (tiedot/->UrakoitsijaTallennettu uusi) {:haetut-urakoitsijat vanhat})]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakoitsija tulos)))
      (is (= (conj vanhat uusi) (:haetut-urakoitsijat tulos)))))

  (testing "Urakoitsijan muokkaaminen"
    (let [vanhat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
          uusi {:id 2 :nimi :bb}
          tulos (e! (tiedot/->UrakoitsijaTallennettu uusi) {:haetut-urakoitsijat vanhat})]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakoitsija tulos)))
      (is (= [{:id 1 :nimi :a} {:id 2 :nimi :bb}] (:haetut-urakoitsijat tulos))))))

(deftest tallentamisen-epaonnistuminen
  (let [tulos (e! (tiedot/->UrakoitsijaEiTallennettu "virhe"))]
    (is (false? (:tallennus-kaynnissa? tulos)))
    (is (nil? (:valittu-urakoitsija tulos)))))

(deftest urakoitsijan-muokkaaminen-lomakkeessa
  (let [urakoitsija {:nimi :foobar}]
    (is (= urakoitsija (:valittu-urakoitsija (e! (tiedot/->UrakoitsijaaMuokattu urakoitsija)))))))

(deftest hakemisen-aloitus
  (vaadi-async-kutsut
    #{tiedot/->UrakoitsijatHaettu tiedot/->UrakoitsijatEiHaettu}

    (is (true? (:urakoitsijoiden-haku-kaynnissa? (e! (tiedot/->HaeUrakoitsijat {:id 1})))))))

(deftest hakemisen-valmistuminen
  (let [urakat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
        tulos (e! (tiedot/->UrakoitsijatHaettu urakat))]
    (is (false? (:urakoitsijoiden-haku-kaynnissa? tulos)))
    (is (= [{:id 1 :nimi :a} {:id 2 :nimi :b}] (:haetut-urakoitsijat tulos)))))

(deftest hakemisen-epaonnistuminen
  (let [tulos (e! (tiedot/->UrakoitsijatEiHaettu "virhe"))]
    (is (false? (:urakoitsijoiden-haku-kaynnissa? tulos)))))

(deftest aloituksen-ajankohdan-maarittely
  (let [tammikuu-2015 (pvm/->pvm "01.01.2015")
        tammikuu-2016 (pvm/->pvm "01.01.2016")
        tammikuu-2017 (pvm/->pvm "01.01.2017")
        helmikuu-2017 (pvm/->pvm "02.02.2017")
        tammikuu-2018 (pvm/->pvm "01.01.2018")
        tammikuu-2019 (pvm/->pvm "01.01.2019")]

    (testing
      "Urakan aloituksen ajankohta keywordiksi"
      (let [nyt tammikuu-2017
           alkava [tammikuu-2018 tammikuu-2019]
           kaynnissa [tammikuu-2016 tammikuu-2018]
           kaynnissa2 [tammikuu-2017 helmikuu-2017]
           kaynnissa3 [tammikuu-2016 tammikuu-2017]
           paattynyt [tammikuu-2015 tammikuu-2016]
           aloitus (partial tiedot/aloitus nyt)
           testaa (fn [haluttu-tila aikavali] (= haluttu-tila (aloitus aikavali)))]
       (is (testaa :alkava alkava))
       (is (testaa :kaynnissa kaynnissa))
       (is (testaa :kaynnissa kaynnissa2))
       (is (testaa :kaynnissa kaynnissa3))
       (is (testaa :paattynyt paattynyt))))

    (testing
      "Urakoiden grouppaaminen ajankohdan mukaan"
      (let [alkava-urakka {::u/id 1 ::u/alkupvm tammikuu-2018 ::u/loppupvm tammikuu-2019}
            kaynnissa-urakka {::u/id 2 ::u/alkupvm tammikuu-2016 ::u/loppupvm tammikuu-2018}
            paattynyt-urakka {::u/id 3 ::u/alkupvm tammikuu-2015 ::u/loppupvm tammikuu-2016}
            urakoitsija {::o/urakat [alkava-urakka kaynnissa-urakka paattynyt-urakka]}]
        (with-redefs
          [pvm/nyt (constantly tammikuu-2017)]
          (is (=
                {:alkava [alkava-urakka]
                 :kaynnissa [kaynnissa-urakka]
                 :paattynyt [paattynyt-urakka]}
                (tiedot/urakoitsijan-urakat urakoitsija))))))

    (testing
      "Urakoiden lukumäärä merkkijonoksi"
      (let [alkava-urakka {::u/id 1 ::u/alkupvm tammikuu-2018 ::u/loppupvm tammikuu-2019}
            alkava-urakka2 {::u/id 2 ::u/alkupvm helmikuu-2017 ::u/loppupvm tammikuu-2019}
            paattynyt-urakka {::u/id 3 ::u/alkupvm tammikuu-2015 ::u/loppupvm tammikuu-2016}
            urakoitsija {::o/urakat [alkava-urakka alkava-urakka2 paattynyt-urakka]}]
        (with-redefs
          [pvm/nyt (constantly tammikuu-2017)]
          (is (= "2 / 0 / 1" (tiedot/urakoitsijan-urakoiden-lukumaarat-str urakoitsija)))
          (is (= "0 / 0 / 0" (tiedot/urakoitsijan-urakoiden-lukumaarat-str {::o/urakat []}))))))

    (testing
      "Urakan aikaväli merkkijonoksi"
      (let [tam-2017 "01.01.2017"
            tam-2018 "01.01.2018"
            alkava-urakka {::u/id 1 ::u/alkupvm (pvm/->pvm tam-2017) ::u/loppupvm (pvm/->pvm tam-2018)}]
        (is (= (str tam-2017 " - " tam-2018) (tiedot/urakan-aikavali-str alkava-urakka)))))))

