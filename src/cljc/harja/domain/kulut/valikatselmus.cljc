(ns harja.domain.kulut.valikatselmus
  (:require
    [harja.domain.urakka :as urakka]
    [harja.domain.muokkaustiedot :as muokkaustiedot]
    [harja.pvm :as pvm]
    [clojure.spec.alpha]
    [harja.kyselyt.specql :as harja-specql]
    #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]]
        :cljs [[specql.impl.registry]]))
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

;; näiden kahden vuoden aikana alkaneilla MHU-urakoilla oli tavoitehinnan oikaisu
;; tyyppiä "Alleviivatun fontin vaikutus tavoitehintaan"
(def alkuvuodet-jolloin-alleviivattuja #{2019 2020})

(defn luokat [urakka]
  (let [yhteiset ["Tiestömuutokset" "Tehtävämuutokset" "Työmäärämuutokset" "Hoitoluokkamuutokset"
                  "Liikennejärjestelyt" "Materiaalit" "Muut"]]
    (sort
      (if (alkuvuodet-jolloin-alleviivattuja (pvm/vuosi (:alkupvm urakka)))
        (conj yhteiset "Alleviivatun fontin vaikutus tavoitehintaan")
        yhteiset))))

(def +tavoitepalkkio-kerroin+ 0.3)
(def +urakoitsijan-osuus-ylityksesta+ 0.3)
(def +maksimi-tavoitepalkkio-prosentti+ 0.03)
