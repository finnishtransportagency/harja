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

(deftest syoton-valiuden-paattely-toimii
  ;; Validit syötöt
  (is (true? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "100")))
  (is (true? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "99,1")))
  (is (true? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "5")))
  (is (true? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "6")))
  (is (true? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "1,3")))

  ;; Rajojen ylitys
  (is (false? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "102")))
  (is (false? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "100,1")))
  (is (false? (nappaimisto/syotto-validi? :kitkamittaus "0")))

  ;; Max merkkimäärien ylitys
  (is (false? (nappaimisto/syotto-validi? :lumisuus "100,1")))
  (is (false? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "100,12")))
  (is (false? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "1451,1")))
  (is (false? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "1,35")))

  ;; Pilkku väärässä paikassa
  (is (false? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "1,")))
  (is (false? (nappaimisto/syotto-validi? :talvihoito-tasaisuus ",12")))
  (is (false? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "134,")))

  ;; Optiot toimii
  (is (true? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "102" {:validoi-rajat? false})))
  (is (true? (nappaimisto/syotto-validi? :talvihoito-tasaisuus "999" {:validoi-rajat? false}))))

