(ns harja.views.vesivaylat.urakka.materiaalit-test
  (:require [harja.views.vesivaylat.urakka.materiaalit :as sut]
            [harja.domain.vesivaylat.materiaali :as m]
            [tuck.core :as tuck]
            [cljs.test :as test :refer-macros [deftest is]]
            [harja.testutils.shared-testutils :as u]
            [harja.testutils :as utils]
            [harja.pvm :as pvm]
            [cljs.core.async :refer [<!]]
            [reagent.core :as r])
  (:require-macros [harja.testutils.macros :refer [komponenttitesti]]
                   [cljs.core.async.macros :refer [go]]))

(test/use-fixtures :each u/komponentti-fixture utils/fake-palvelut-fixture)

(def listaus
  [{::m/nimi "Poiju" ::m/alkuperainen-maara 20  ::m/maara-nyt 18 ::m/lisatieto "annettu 20 poijua"
    ::m/muutokset
    [{::m/pvm (pvm/->pvm "1.5.2017") ::m/maara -3 ::m/lisatieto "käytettiin 3kpl"}
     {::m/pvm (pvm/->pvm "2.5.2017") ::m/maara -1 ::m/lisatieto "yksi upposi mereen"}
     {::m/pvm (pvm/->pvm "10.5.2017") ::m/maara 2 ::m/lisatieto "saatiin pajalta 2 lisää"}]}])

(def alkutila
  {:urakka-id 1
   :materiaalilistaus nil})

(deftest materiaalit
  (let [app (r/atom alkutila)
        ensimmainen-rivi #(for [solu (range 1 4)]
                            (u/text (u/grid-solu "vv-materiaalilistaus" 0 solu)))
        haku (utils/fake-palvelukutsu :hae-vesivayla-materiaalilistaus
                                      (constantly listaus))
        tallennus (utils/fake-palvelukutsu
                   :kirjaa-vesivayla-materiaali
                   (fn [tiedot]
                     (let [listaus (:materiaalilistaus @app)]
                       (update listaus 0
                               (fn [poijut]
                                 (-> poijut
                                     (update ::m/maara-nyt + (::m/maara tiedot))
                                     (update ::m/muutokset conj
                                             (select-keys tiedot #{::m/pvm ::m/lisatieto ::m/maara}))))))))]

    (komponenttitesti
     [tuck/tuck app sut/materiaalit*]
     --
     (<! haku)
     --
     "Alkutilanteessa oikeat tiedot: Poiju, 20 alkup, 18 nyt"
     (is (= '("Poiju" "20" "18") (ensimmainen-rivi)))
     --
     "Avataan vetolaatikko"
     (is (nil? (u/sel1 :.vetolaatikko-auki)))
     (u/click (u/grid-solu "vv-materiaalilistaus" 0 0))
     --
     "Katsotaan, että vetolaatikon muutoslokissa on oikeat rivit"
     (is (u/sel1 :.vetolaatikko-auki))
     (is (= 3 (count (u/sel ".vv-materiaaliloki tr"))))
     (is (= "saatiin pajalta 2 lisää" (u/text (u/sel1 ".vv-materiaaliloki tr:nth-child(3) td:nth-child(3)"))))
     --
     "Kirjataan uusi poijun käyttö"
     (u/click ".vv-materiaalin-kirjaus button")
     --
     (is (u/sel1 ".vv-materiaalin-kirjaus .lomake"))
     (u/change (u/lomake-input "maara") "-10")
     --
     (is (= -10 (get-in @app [:kirjaa-materiaali ::m/maara])))
     (u/change (u/sel1 "label[for='lisatieto'] + .kentta-text textarea") "hukattiin 10kpl")
     --
     (u/click ".leijuke-sisalto button.nappi-ensisijainen")
     --
     (<! tallennus)
     --
     "Tarkistetaan tallennuksen jälkeen uusi tila"
     (is (= '("Poiju" "20" "8") (ensimmainen-rivi)))
     (is (= 4 (count (u/sel ".vv-materiaaliloki tr"))))
     (is (= "hukattiin 10kpl" (u/text (u/sel1 ".vv-materiaaliloki tr:nth-child(4) td:nth-child(3)"))))
     (is (nil? (u/sel1 ".vv-materiaalin-kirjaus .lomake"))))))
