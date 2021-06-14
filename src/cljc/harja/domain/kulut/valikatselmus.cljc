(ns harja.domain.kulut.valikatselmus
  (:require
    [harja.domain.urakka :as urakka]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]])
    [specql.rel :as rel]
    [specql.core :as specql])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["tavoitehinnan_oikaisu" ::tavoitehinnan-oikaisu
   {"id" ::oikaisun-id
    "urakka-id" ::urakka/id
    "luoja-id" ::muokkaustiedot/luoja-id
    "luotu" ::muokkaustiedot/luotu
    "muokkaaja-id" ::muokkaustiedot/muokkaaja-id
    "muokattu" ::muokkaustiedot/muokattu
    "poistettu" ::muokkaustiedot/poistettu
    "otsikko" ::otsikko
    "selite" ::selite
    "summa" ::summa
    "hoitokausi" ::hoitokausi}])

(def oikaisu-avaimet (specql/columns ::tavoitehinnan-oikaisu))

