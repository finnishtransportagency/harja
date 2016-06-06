(ns harja.views.urakka.paallystysilmoitukset-test
  (:require
    [cljs-time.core :as t]
    [cljs.test :as test :refer-macros [deftest is]]
    [harja.loki :refer [log]]
    [harja.domain.paallystysilmoitus :as pot]
    [harja.domain.tierekisteri :as tierekisteri-domain]
    [harja.ui.tierekisteri :as tierekisteri]))

(deftest tien-pituus-laskettu-oikein
  (let [tie1 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 3 :tr-loppuetaisyys 5}
        tie2 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 5 :tr-loppuetaisyys 5}
        tie3 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 3 :tr-loppuetaisyys -100}
        tie4 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 1 :tr-loppuetaisyys 2}
        tie5 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 0 :tr-loppuetaisyys 1}
        tie6 {:tr-alkuosa 1 :tr-loppuosa 1 :tr-alkuetaisyys 1}]
    (is (= (tierekisteri-domain/laske-tien-pituus tie1) 2))
    (is (= (tierekisteri-domain/laske-tien-pituus tie2) 0))
    (is (= (tierekisteri-domain/laske-tien-pituus tie3) 103))
    (is (= (tierekisteri-domain/laske-tien-pituus tie4) 1))
    (is (= (tierekisteri-domain/laske-tien-pituus tie5) 1))
    (is (= (tierekisteri-domain/laske-tien-pituus tie6) nil))))

(deftest muutos-kokonaishintaan-laskettu-oikein
  (let [tyot [{:tilattu-maara 10 :toteutunut-maara 15 :yksikkohinta 1}
              {:tilattu-maara 15 :toteutunut-maara 15  :yksikkohinta 666}
              {:tilattu-maara 4 :toteutunut-maara 5 :yksikkohinta 8}]
    tyot2 [{:tilattu-maara 4 :toteutunut-maara 2 :yksikkohinta 15}]]
    (is (= (pot/laske-muutokset-kokonaishintaan tyot) 13))
    (is (= (pot/laske-muutokset-kokonaishintaan tyot2) -30))))
