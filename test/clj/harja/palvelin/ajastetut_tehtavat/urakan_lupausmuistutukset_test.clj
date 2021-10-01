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

(deftest hae-muistutettavat-urakat-toimii
  (let [testitietokanta (:db jarjestelma)
        nyt (pvm/->pvm (str "01.10.2019"))
        urakat-muistutuksista (lupausmuistutukset/hae-muistutettavat-urakat testitietokanta nyt 2019)]
    (is
      (seq urakat-muistutuksista)
      "Löytyy ainakin yksi muistutettava urakka (Oulu MHU).
      Ei haittaa jos testidataan lisätään urakoita myöhemmin.")))
