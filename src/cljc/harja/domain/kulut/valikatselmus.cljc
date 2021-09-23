(ns harja.domain.kulut.valikatselmus
  (:require
    [harja.domain.urakka :as urakka]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [clojure.spec.alpha]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]]
        :cljs [[specql.impl.registry]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["urakka_paatos" ::urakka-paatos
   {"id" ::paatoksen-id
    "urakka-id" ::urakka/id
    "hoitokauden-alkuvuosi" ::hoitokauden-alkuvuosi
    "hinnan-erotus" ::hinnan-erotus
    "urakoitsijan-maksu" ::urakoitsijan-maksu
    "tilaajan-maksu" ::tilaajan-maksu
    "siirto" ::siirto
    "tyyppi" ::tyyppi
    "luoja-id" ::muokkaustiedot/luoja-id
    "luotu" ::muokkaustiedot/luotu
    "muokattu" ::muokkaustiedot/muokattu
    "muokkaaja-id" ::muokkaustiedot/muokkaaja-id
    "poistettu" ::muokkaustiedot/poistettu?}]
  ["tavoitehinnan_oikaisu" ::tavoitehinnan-oikaisu
   {"id" ::oikaisun-id
    "urakka-id" ::urakka/id
    "luoja-id" ::muokkaustiedot/luoja-id
    "luotu" ::muokkaustiedot/luotu
    "muokkaaja-id" ::muokkaustiedot/muokkaaja-id
    "muokattu" ::muokkaustiedot/muokattu
    "poistettu" ::muokkaustiedot/poistettu?
    "otsikko" ::otsikko
    "selite" ::selite
    "summa" ::summa
    "hoitokauden-alkuvuosi" ::hoitokauden-alkuvuosi}])

(def paatosten-tyypit
  #{::tavoitehinnan-ylitys ::tavoitehinnan-alitus ::kattohinnan-ylitys ::lupaus-bonus ::lupaus-sanktio})

(def luokat
  #{"Tiestömuutos" "Hoitoluokkamuutokset" "Yleiset Liikennejärjestelyt" "Bonukset ja sanktiot" "Muut"})

(def +tavoitepalkkio-kerroin+ 0.3)
(def +maksimi-tavoitepalkkio-prosentti+ 0.03)