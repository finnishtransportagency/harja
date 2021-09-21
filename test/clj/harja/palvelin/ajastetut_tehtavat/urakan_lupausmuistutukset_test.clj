(ns harja.palvelin.ajastetut-tehtavat.urakan-lupausmuistutukset-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.pvm :as pvm]
            [harja.kyselyt.lupaukset :as lupaukset-q]
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
        alkupvm (pvm/->pvm (str "01.10.2019"))
        oulumhu-loppuu (pvm/->pvm (str "30.09.2023"))
        nyt (pvm/->pvm (str "01.10.2019"))
        urakoiden-oikea-maara (if (pvm/ennen? alkupvm oulumhu-loppuu)
                                1 0)
        urakat-tietokannasta (lupaukset-q/hae-kaynnissa-olevat-lupaus-urakat testitietokanta {:alkupvm alkupvm
                                                                                              :nykyhetki nyt})
        urakat-muistutuksista (lupausmuistutukset/muistuta-lupauksista testitietokanta nil nil (pvm/luo-pvm 2019 10 1) 2019 true)]
    (is (= urakoiden-oikea-maara (count urakat-tietokannasta)) "Löytyy yksi niin kauan kuin Oulu MHU on käynnissä (2023)")
    (is (= urakat-tietokannasta urakat-muistutuksista) "Muistutusprosessin kautta pitäisi tulla samat urakat")))
