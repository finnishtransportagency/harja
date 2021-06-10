(ns harja.domain.kulut.valikatselmus
  (:require
    [harja.domain.urakka :as urakka]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]])
    [specql.rel :as rel])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["tavoitehinnan_oikaisu" ::tavoitehinnan-oikaisu
   {"urakka-id" ::urakka-id
    "luoja-id" ::luoja-id
    "luotu" ::luotu
    "muokkaaja-id" ::muokkaaja-id
    "muokattu" ::muokattu
    "otsikko" ::otsikko
    "selite" ::selite
    "summa" ::summa
    "hoitokausi" ::hoitokausi
    "poistettu" ::poistettu}])

(defn oikaisu-avaimet->speql [{:keys [urakka-id
                                      luoja-id
                                      luotu
                                      muokkaaja-id
                                      muokattu
                                      otsikko
                                      summa
                                      selite
                                      hoitokausi]}]
  {::urakka-id urakka-id
   ::luoja-id luoja-id
   ::luotu luotu
   ::muokkaaja-id muokkaaja-id
   ::muokattu muokattu
   ::otsikko otsikko
   ::summa summa
   ::selite selite
   ::hoitokausi hoitokausi})
