(ns harja.palvelin.ajastetut-tehtavat.urakan-lupausmuistutukset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.pvm :as pvm]
            [harja.kyselyt.lupaus-kyselyt :as lupaus-kyselyt]
            [harja.palvelin.ajastetut-tehtavat.urakan-lupausmuistutukset :as lupausmuistutukset]))

(defn jarjestelma-fixture [testit]
  (pudota-ja-luo-testitietokanta-templatesta)
  (alter-var-root
    #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)))))

  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :once jarjestelma-fixture)

(defn- etsi-urakka [urakat nimi]
  (->>
    urakat
    (filter #(= (:nimi %) nimi))
    first))

(defn- urakat->oulun-mhu [urakat]
  (etsi-urakka urakat "Oulun MHU 2019-2024"))

(defn- urakat->iin-mhu [urakat]
  (etsi-urakka urakat "Iin MHU 2021-2026"))

(deftest hae-muistutettavat-urakat-toimii-2019-alkavalle-urakalle
  (testing "Päivää ennen urakan alkamista"
    (let [nyt (pvm/->pvm (str "30.09.2019"))
          urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2019)]
      (is (empty? urakat)) "Ei muistuteta ennnen urakan alkamista (1.10.)"))

  (testing "Urakan ensimmäisenä päivänä"
    (let [nyt (pvm/->pvm (str "01.10.2019"))
          urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2019)]
      (is (urakat->oulun-mhu urakat) "Oulun MHU on muistutettava 2019-alkuinen urakka")
      (is (not (urakat->iin-mhu urakat)) "Iin MHU ei ole 2019-alkuinen urakka")))

  (testing "Urakan viimeisenä päivänä"
    (let [nyt (pvm/->pvm (str "30.09.2024"))
          urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2019)]
      (is (urakat->oulun-mhu urakat) "Oulun MHU on muistutettava 2019-alkuinen urakka")
      (is (not (urakat->iin-mhu urakat)) "Iin MHU ei ole 2019-alkuinen urakka")))

  (testing "Yksi päivä urakan päättymisen jälkeen"
    (let [nyt (pvm/->pvm (str "01.10.2024"))
          urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2019)]
      (is (urakat->oulun-mhu urakat) "Oulun MHU on muistutettava 2019-alkuinen urakka")
      (is (not (urakat->iin-mhu urakat)) "Iin MHU ei ole 2019-alkuinen urakka")))

  (testing "Kolme kuukautta urakan päättymisen jälkeen"
    (let [nyt (pvm/->pvm (str "01.1.2025"))
          urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2019)]
      (is (empty? urakat) "Ei muistuteta kolme kuukautta urakan päättymisen jälkeen"))))

(deftest hae-muistutettavat-urakat-toimii-2021-alkavalle-urakalle
  (let [nyt (pvm/->pvm (str "01.10.2021"))
        urakat (lupausmuistutukset/hae-kaynnissa-olevat-urakat (:db jarjestelma) nyt 2021)]
    (is (urakat->iin-mhu urakat) "Iin MHU on muistutettava 2021-alkuinen urakka")
    (is (not (urakat->oulun-mhu urakat)) "Oulun MHU ei ole 2021-alkuinen urakka")))
