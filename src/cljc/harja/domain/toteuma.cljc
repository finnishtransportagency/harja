(ns harja.domain.toteuma
  (:require
    [clojure.spec.alpha :as s]
    [harja.domain.muokkaustiedot :as m]

    #?@(:clj [
    [harja.kyselyt.specql-db :refer [define-tables]]
    [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["toteuma" ::toteuma
   {"urakka" ::urakka-id
    "sopimus" ::sopimus-id
    "suorittajan_ytunnus" ::suorittajan-ytunnus
    "suorittajan_nimi" ::suorittajan-nimi
    "ulkoinen_id " ::ulkoinen-id
    "tr_numero" ::tr-numero
    "tr_alkuosa" ::tr-alkuosa
    "tr_alkuetaisyys" ::tr-alkuetaisyys
    "tr_loppuosa" ::tr-loppuosa
    "tr_loppuetaisyys" ::tr-loppuetaisyys
    "muokattu" ::m/muokattu
    "muokkaaja" ::m/muokkaaja-id
    "luotu" ::m/luotu
    "luoja" ::m/luoja-id
    "poistettu" ::m/poistettu?}])
