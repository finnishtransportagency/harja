(ns harja.views.urakka.yleiset-test
  "Yleiset näkymän testi"
  (:require [cljs.test :as t :refer-macros [deftest is testing async]]
            [harja.testutils.shared-testutils :as u]
            [harja.testutils :refer [fake-palvelut-fixture fake-palvelukutsu
                                     jvh-fixture]]
            [reagent.core :as r]
            [harja.views.urakka.yleiset :as yleiset]
            [harja.views.urakka.yleiset.paivystajat :as paivystajat]
            [harja.pvm :as pvm])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]))

(t/use-fixtures :each u/komponentti-fixture)

(def paivystajadata
  [{:urakka_tyyppi "hoito", :vastuuhenkilo true, :sahkoposti "kyostiyit@example.org", :urakoitsija_nimi "YIT Rakennus Oy",
    :urakka_nimi "Oulun alueurakka 2014-2019", :sukunimi "Kallio", :varahenkilo false, :yhteyshenkilo_id 104,
    :urakka_loppupvm (pvm/->pvm "30.09.2019"), :urakka_alkupvm (pvm/->pvm "1.10.2014"), :id  3, :urakoitsija_ytunnus "1565583-5",
    :urakoitsija_id 11, :matkapuhelin "", :etunimi "Kyösti", :urakoitsija_tyyppi "urakoitsija", :alku (pvm/->pvm "1.12.2015"),
    :urakka_id  4, :loppu (pvm/->pvm "6.12.2015"), :organisaatio {:tyyppi :hallintayksikko, :id 9, :nimi "Pohjois-Pohjanmaa"}, :tyopuhelin ""}
   {:urakka_tyyppi "hoito", :vastuuhenkilo false, :sahkoposti "seppoyit@example.org", :urakoitsija_nimi "YIT Rakennus Oy",
    :urakka_nimi "Oulun alueurakka 2014-2019", :sukunimi "Taalasmaa", :varahenkilo true, :yhteyshenkilo_id 103,
    :urakka_loppupvm (pvm/->pvm "30.09.2019"), :urakka_alkupvm (pvm/->pvm "1.10.2014"), :id  2, :urakoitsija_ytunnus "1565583-5",
    :urakoitsija_id 11, :matkapuhelin "044", :etunimi "Seppo", :urakoitsija_tyyppi "urakoitsija", :alku (pvm/->pvm "13.11.2015"),
    :urakka_id  4, :loppu (pvm/->pvm "30.11.2015"), :organisaatio {:tyyppi :hallintayksikko, :id 9, :nimi "Pohjois-Pohjanmaa"}, :tyopuhelin "0505555555"}
   {:urakka_tyyppi "hoito", :vastuuhenkilo false, :sahkoposti "ismoyit@example.org", :urakoitsija_nimi "YIT Rakennus Oy",
    :urakka_nimi "Oulun alueurakka 2014-2019", :sukunimi "Laitela", :varahenkilo true, :yhteyshenkilo_id 102,
    :urakka_loppupvm (pvm/->pvm "30.09.2019"), :urakka_alkupvm (pvm/->pvm "1.10.2014"), :id  1, :urakoitsija_ytunnus "1565583-5",
    :urakoitsija_id 11, :matkapuhelin "0400123456", :etunimi "Ismo", :urakoitsija_tyyppi "urakoitsija", :alku (pvm/->pvm "1.11.2015"),
    :urakka_id  4, :loppu (pvm/->pvm "11.11.2015"), :organisaatio {:tyyppi :hallintayksikko, :id 9, :nimi "Pohjois-Pohjanmaa"}, :tyopuhelin "000"}])

(def urakka
  {:id          4 :nimi "Oulun urakka"
   :urakoitsija {:nimi "YIT Rakennus Oyj" :id 2}
   :hallintayksikko {:nimi "Pohjois-Pohjanmaa" :id 9}})

(deftest paivystajat
  (let [data (r/atom paivystajadata)]
    (komponenttitesti
      [paivystajat/paivystajalista urakka
       @data
       (fn [uudet]
         (reset! data uudet))]

      "Aluksi kolme päivystäjäriviä joista ei yksikään boldattu (koska päivystyksiä ei voimassa)"
      (is (= 3 (count (u/sel [:tbody :tr]))))
      (is (= 0 (count (u/sel [:tbody :tr.bold]))))
      ;; muutetaan eka päivystäjän loppuajaksi 2040, jotta päivystys voimassa ja boldattu
      (swap! data assoc-in [0 :loppu] (pvm/->pvm "1.1.2040"))
      --
      (is (= 1 (count (u/sel [:tbody :tr.bold]))))

      (swap! data assoc-in [1 :loppu] (pvm/->pvm "1.1.2042"))
      --
      (is (= 2 (count (u/sel [:tbody :tr.bold]))))

      (swap! data assoc-in [0 :loppu] (pvm/->pvm "1.1.2015"))
      --
      (is (= 1 (count (u/sel [:tbody :tr.bold]))))

      "Muokkaustoimintojen nappien määrä kun toiminnot kiinni"
      (is (= 1 (count (u/sel [:button]))))
      (is (= 1 (count (u/sel [:.muokkaustoiminnot :button]))))
      (is (= 0 (count (u/sel [:.livi-alasveto :button]))))
      (u/click :button.nappi-ensisijainen)
      --
      "Muokkaustoimintojen nappien määrä kun toiminnot avattu"
      (is (= 7 (count (u/sel [:button]))))
      (is (= 4 (count (u/sel [:.muokkaustoiminnot :button]))))
      (is (= 3 (count (u/sel [:.livi-alasveto :button])))))))
