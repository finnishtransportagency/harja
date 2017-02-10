(ns harja-laadunseuranta.tiedot.nappaimisto-test
  (:require [cljs.test :as t :refer-macros [deftest is testing run-tests async]]
            [harja-laadunseuranta.tiedot.nappaimisto :as nappaimisto]))

(deftest nykyisen-syotto-osan-max-merkkimaara-saavutettu-toimii
  (is (false? (nappaimisto/nykyisen-syotto-osan-max-merkkimaara-saavutettu? :lumisuus "1")))
  (is (false? (nappaimisto/nykyisen-syotto-osan-max-merkkimaara-saavutettu? :lumisuus "10")))
  (is (true? (nappaimisto/nykyisen-syotto-osan-max-merkkimaara-saavutettu? :lumisuus "100")))

  (is (false? (nappaimisto/nykyisen-syotto-osan-max-merkkimaara-saavutettu? :talvihoito-tasaisuus "1")))
  (is (false? (nappaimisto/nykyisen-syotto-osan-max-merkkimaara-saavutettu? :talvihoito-tasaisuus "10")))
  (is (true? (nappaimisto/nykyisen-syotto-osan-max-merkkimaara-saavutettu? :talvihoito-tasaisuus "100")))
  (is (false? (nappaimisto/nykyisen-syotto-osan-max-merkkimaara-saavutettu? :talvihoito-tasaisuus "100,")))
  (is (true? (nappaimisto/nykyisen-syotto-osan-max-merkkimaara-saavutettu? :talvihoito-tasaisuus "100,0")))
  (is (true? (nappaimisto/nykyisen-syotto-osan-max-merkkimaara-saavutettu? :talvihoito-tasaisuus "100,01"))))