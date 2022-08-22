(ns harja.domain.kulut.valikatselmus
  (:require
    [harja.domain.urakka :as urakka]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [clojure.spec.alpha]
    [harja.kyselyt.specql :as harja-specql]
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
    "lupaus-luvatut-pisteet" ::lupaus-luvatut-pisteet
    "lupaus-toteutuneet-pisteet" ::lupaus-toteutuneet-pisteet
    "lupaus-tavoitehinta" ::lupaus-tavoitehinta
    "luoja-id" ::muokkaustiedot/luoja-id
    "luotu" ::muokkaustiedot/luotu
    "muokattu" ::muokkaustiedot/muokattu
    "muokkaaja-id" ::muokkaustiedot/muokkaaja-id
    "poistettu" ::muokkaustiedot/poistettu?}
   #?(:clj {::lupaus-tavoitehinta (specql.transform/transform (harja-specql/->BigDecimalTransform))})]
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
    "hoitokauden-alkuvuosi" ::hoitokauden-alkuvuosi}]
  ["kattohinnan_oikaisu" ::kattohinnan-oikaisu
   {"id" ::kattohinnan-oikaisun-id
    "urakka-id" ::urakka/id
    "luoja-id" ::muokkaustiedot/luoja-id
    "luotu" ::muokkaustiedot/luotu
    "muokkaaja-id" ::muokkaustiedot/muokkaaja-id
    "muokattu" ::muokkaustiedot/muokattu
    "uusi-kattohinta" ::uusi-kattohinta
    "hoitokauden-alkuvuosi" ::hoitokauden-alkuvuosi
    "poistettu" ::muokkaustiedot/poistettu?}])

(def paatosten-tyypit
  #{::tavoitehinnan-ylitys ::tavoitehinnan-alitus ::kattohinnan-ylitys ::lupaus-bonus ::lupaus-sanktio})

(def luokat
  #{"Tiestömuutokset" "Tehtävämuutokset" "Työmäärämuutokset" "Hoitoluokkamuutokset"
    "Liikennejärjestelyt" "Bonukset ja sanktiot" "Alleviivatun fontin vaikutus tavoitehintaan"
    "Materiaalit" "Muut"})

(def +tavoitepalkkio-kerroin+ 0.3)
(def +urakoitsijan-osuus-ylityksesta+ 0.3)
(def +maksimi-tavoitepalkkio-prosentti+ 0.03)