(ns harja.tiedot.vesivaylat.urakoiden-luonti-test
  (:require [harja.tiedot.vesivaylat.urakoiden-luonti :as u]
            [clojure.test :refer-macros [deftest is testing]]
            [harja.tuck-apurit :refer-macros [vaadi-async-kutsut] :refer [e! e-tila!]]))

(deftest urakan-valinta
  (let [ur {:foobar 1}]
    (is (= ur (:valittu-urakka (e! u/->ValitseUrakka ur))))))

(deftest nakymaan-tuleminen
  (is (true? (:nakymassa? (e! u/->Nakymassa? true))))
  (is (false? (:nakymassa? (e! u/->Nakymassa? false)))))

(deftest uuden-urakan-luonnin-aloitus
  (is (= u/uusi-urakka (:valittu-urakka (e! u/->UusiUrakka)))))

(deftest tallentamisen-aloitus
  (vaadi-async-kutsut
    #{u/->UrakkaTallennettu u/->UrakkaEiTallennettu}

    (is (true? (:tallennus-kaynnissa? (e-tila! u/->TallennaUrakka {:id 1} {:haetut-urakat []}))))))

(deftest tallentamisen-valmistuminen
  (testing "Uuden urakan tallentaminen"
    (let [vanhat [{:id 1} {:id 2}]
          uusi {:id 3}
          tulos (e-tila! u/->UrakkaTallennettu uusi {:haetut-urakat vanhat})]
      (is (false? (:tallennus-kaynnissa? tulos)))
      (is (nil? (:valittu-urakka tulos)))
      (is (= (conj vanhat uusi) (:haetut-urakat tulos)))))

  (testing "Urakan muokkaaminen"
    (let [vanhat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
          uusi {:id 2 :nimi :bb}
          tulos (e-tila! u/->UrakkaTallennettu uusi {:haetut-urakat vanhat})]
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

(deftest hakemisen-aloitus
  (vaadi-async-kutsut
    #{u/->UrakatHaettu u/->UrakatEiHaettu}
    (is (true? (:urakoiden-haku-kaynnissa? (e! u/->HaeUrakat {:id 1}))))))

(deftest hakemisen-valmistuminen
  (let [urakat [{:id 1 :nimi :a} {:id 2 :nimi :b}]
        tulos (e! u/->UrakatHaettu urakat)]
    (is (false? (:urakoiden-haku-kaynnissa? tulos)))
    (is (= [{:id 1 :nimi :a} {:id 2 :nimi :b}] (:haetut-urakat tulos)))))

(deftest hakemisen-epaonnistuminen
  (let [tulos (e! u/->UrakatEiHaettu "virhe")]
    (is (false? (:urakoiden-haku-kaynnissa? tulos)))))

(deftest sopimuksen-paivittaminen
  (let [testaa (fn [tila annettu haluttu]
                 (= (-> (e-tila! u/->PaivitaSopimuksetGrid annettu {:valittu-urakka {:sopimukset tila}})
                        (get-in [:valittu-urakka :sopimukset]))
                    haluttu))]
    (testing "Rivin lisääminen gridiin"
      (is (testaa [{:id 1 :paasopimus nil}]
                  [{:id 1 :paasopimus nil} {:id -2 :paasopimus nil}]
                  [{:id 1 :paasopimus nil} {:id -2 :paasopimus nil}])))

    (testing "Rivin asettaminen sopimukseksi gridiin"
      (is (testaa [{:id 1 :paasopimus nil} {:id -2 :paasopimus nil}]
                  [{:id 1 :paasopimus nil} {:id 2 :paasopimus nil}]
                  [{:id 1 :paasopimus nil} {:id 2 :paasopimus nil}])))

    ;; Pääsopimus asetetaan muualla..

    (testing "Sopimuksen lisääminen gridiin, kun pääsopimus on jo asetettu"
      (is (testaa [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1}]
                  [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id -3 :paasopimus nil}]
                  [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id -3 :paasopimus 1}])))

    (testing "Rivin poistaminen gridistä"
      (is (testaa [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id -3 :paasopimus 1}]
                  [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1 :poistettu true} {:id -3 :paasopimus nil :poistettu true}]
                  [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1 :poistettu true} {:id -3 :paasopimus 1 :poistettu true}])))))

(deftest lomakevaihtoehtojen-hakemisen-aloitus
  (vaadi-async-kutsut
    #{u/->LomakevaihtoehdotHaettu u/->LomakevaihtoehdotEiHaettu}
    (is (= {:foo :bar} (e-tila! u/->HaeLomakevaihtoehdot {:id 1} {:foo :bar})))))

(deftest lomakevaihtoehtojen-hakemisen-valmistuminen
  (let [hy [{:id 1}]
        ur [{:id 2}]
        h [{:id 3}]
        s [{:id 4}]
        payload {:hallintayksikot hy
                 :urakoitsijat ur
                 :hankkeet h
                 :sopimukset s}
        app (e! u/->LomakevaihtoehdotHaettu payload)]
    (is (= hy (:haetut-hallintayksikot app)))
    (is (= ur (:haetut-urakoitsijat app)))
    (is (= h (:haetut-hankkeet app)))
    (is (= s (:haetut-sopimukset app)))))

(deftest lomakevaihtoehtojen-hakemisen-epaonnistuminen
  (is (= {:foo :bar} (e-tila! u/->LomakevaihtoehdotEiHaettu "virhe" {:foo :bar}))))

(deftest sahke-lahetyksen-aloitus
  (vaadi-async-kutsut
    #{u/->SahkeeseenLahetetty u/->SahkeeseenEiLahetetty}
    (is (= {:kaynnissa-olevat-sahkelahetykset #{1}}
           (e-tila! u/->LahetaUrakkaSahkeeseen {:id 1} {:kaynnissa-olevat-sahkelahetykset #{}})))))

(deftest sahke-lahetyksen-valmistuminen
  (is (= {:kaynnissa-olevat-sahkelahetykset #{}}
         (e-tila! u/->SahkeeseenLahetetty {} {:id 1} {:kaynnissa-olevat-sahkelahetykset #{1}}))))

(deftest sahke-lahetyksen-epaonnistuminen
  (is (= {:kaynnissa-olevat-sahkelahetykset #{}}
         (e-tila! u/->SahkeeseenEiLahetetty "virhe" {:id 1} {:kaynnissa-olevat-sahkelahetykset #{1}}))))

(deftest paasopimuksen-kasittely
  (testing "Löydetään aina vain yksi pääsopimus"
    (is (false? (sequential? (u/paasopimus [{:id 1 :paasopimus nil}
                                   {:id 2 :paasopimus nil}
                                   {:id 3 :paasopimus 1}
                                   {:id 4 :paasopimus 2}])))))

  (testing "Pääsopimus löytyy sopimusten joukosta"
    (is (= {:id 1 :paasopimus nil} (u/paasopimus [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id 3 :paasopimus 1}])))
    (is (= {:id 1 :paasopimus nil} (u/paasopimus [{:id 2 :paasopimus 1} {:id 1 :paasopimus nil}]))))

  (testing "Jos pääsopimusta ei ole, sitä ei myöskään palauteta"
    (is (= nil (u/paasopimus [{:id 1 :paasopimus 2} {:id 3 :paasopimus 2}])))
    (is (= nil (u/paasopimus [{:id 1 :paasopimus nil} {:id 3 :paasopimus nil}])))
    (is (= nil (u/paasopimus [])))
    (is (= nil (u/paasopimus [{:id 1 :paasopimus nil}])))
    (is (= nil (u/paasopimus [{:id nil :paasopimus nil}]))))

  (testing "Pääsopimusta päätellessä ei välitetä poistetuista sopimuksista tai uusista riveistä"
    (is (= nil (u/paasopimus [{:id 1 :paasopimus nil} {:id 3 :paasopimus 1 :poistettu true}])))
    (is (= nil (u/paasopimus [{:id 1 :paasopimus nil} {:id- 3 :paasopimus 1}]))))

  (testing "Sopimus tunnistetaan pääsopimukseksi"
    (is (true? (u/paasopimus? [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id 3 :paasopimus 1}] {:id 1 :paasopimus nil})))
    (is (true? (u/paasopimus? [{:id 2 :paasopimus 1} {:id 1 :paasopimus nil}] {:id 1 :paasopimus nil}))))

  (testing "Tunnistetaan, että sopimus ei ole pääsopimus"
    (is (false? (u/paasopimus? [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id 3 :paasopimus 1}] {:id 2 :paasopimus 1})))
    (is (false? (u/paasopimus? [{:id 2 :paasopimus 1} {:id 1 :paasopimus nil}] {:id 2 :paasopimus 1}))))

  (testing "Jos pääsopimusta ei ole, sopimusta ei tunnisteta pääsopimukseksi"
    (is (false? (u/paasopimus? [{:id 1 :paasopimus nil} {:id 2 :paasopimus nil} {:id 3 :paasopimus nil}] {:id 2 :paasopimus nil})))
    (is (false? (u/paasopimus? [{:id 2 :paasopimus nil} {:id 1 :paasopimus nil}] {:id 1 :paasopimus nil})))
    (is (false? (u/paasopimus? [{:id 2 :paasopimus nil} {:id 1 :paasopimus nil}] {:id nil :paasopimus nil}))))

  (testing "Uuden pääsopimuksen asettaminen"
    (is (= [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1}]
           (u/sopimukset-paasopimuksella [{:id 1 :paasopimus nil} {:id 2 :paasopimus nil}]
                                         {:id 1 :paasopimus nil})))
    (is (= [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id 3 :paasopimus 1}]
           (u/sopimukset-paasopimuksella [{:id 1 :paasopimus nil} {:id 2 :paasopimus nil} {:id 3 :paasopimus nil}]
                                         {:id 1 :paasopimus nil})))
    (is (= [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id -3 :paasopimus 1}]
           (u/sopimukset-paasopimuksella [{:id 1 :paasopimus nil} {:id 2 :paasopimus nil} {:id -3 :paasopimus nil}]
                                         {:id 1 :paasopimus nil})))
    (is (= [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id 3 :paasopimus 1 :poistettu true}]
           (u/sopimukset-paasopimuksella [{:id 1 :paasopimus nil} {:id 2 :paasopimus nil} {:id 3 :paasopimus nil :poistettu true}]
                                         {:id 1 :paasopimus nil}))))

  (testing "Pääsopimuksen muuttaminen"
    (is (= [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1}]
           (u/sopimukset-paasopimuksella [{:id 1 :paasopimus 2} {:id 2 :paasopimus nil}]
                                         {:id 1 :paasopimus nil})))
    (is (= [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id 3 :paasopimus 1}]
           (u/sopimukset-paasopimuksella [{:id 1 :paasopimus 2} {:id 2 :paasopimus nil} {:id 3 :paasopimus 2}]
                                         {:id 1 :paasopimus nil})))
    (is (= [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id -3 :paasopimus 1}]
           (u/sopimukset-paasopimuksella [{:id 1 :paasopimus 2} {:id 2 :paasopimus nil} {:id -3 :paasopimus 2}]
                                         {:id 1 :paasopimus nil})))
    (is (= [{:id 1 :paasopimus nil} {:id 2 :paasopimus 1} {:id 3 :paasopimus 1 :poistettu true}]
           (u/sopimukset-paasopimuksella [{:id 1 :paasopimus 2} {:id 2 :paasopimus nil} {:id 3 :paasopimus 2 :poistettu true}]
                                         {:id 1 :paasopimus nil})))))

(deftest urakan-sopimusvaihtoehdot
  (let [kaikki-sopimukset [{:id 1 :urakka {:id 1}} {:id 2 :urakka {:id 1}} {:id 3 :urakka nil} {:id 4 :urakka nil}]
        urakan-sopimukset [{:id 1 :urakka {:id 1}} {:id 2 :urakka {:id 1}} {:id 3 :urakka nil}]]
    (is (= [{:id 4 :urakka nil}] (u/vapaat-sopimukset kaikki-sopimukset urakan-sopimukset)))
    (is (true? (empty? (u/vapaat-sopimukset [{:id 1 :urakka {:id 1}} {:id 2 :urakka {:id 1}} {:id 3 :urakka {:id 1}} {:id 4 :urakka {:id 1}}] urakan-sopimukset))))
    (is (true? (empty? (u/vapaat-sopimukset [{:id 1 :urakka nil}] [{:id 1 :urakka nil}])))))

  (is (true? (u/vapaa-sopimus? {:urakka nil})))
  (is (false? (u/vapaa-sopimus? {:urakka {:id 1}}))))