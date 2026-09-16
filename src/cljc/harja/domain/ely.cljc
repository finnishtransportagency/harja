(ns harja.domain.ely
  "ELY-alueiden oma domain (kannassa tallentuu organisaatio-tauluun)"
  #?@(:clj  [
             (:require [clojure.spec.alpha :as s]
                       [harja.kyselyt.specql-db :refer [define-tables]]
                       )]
      :cljs [(:require [clojure.spec.alpha :as s]
               [specql.impl.registry]
               [specql.data-types])
             (:require-macros
               [harja.kyselyt.specql-db :refer [define-tables]])]))

(def elynumerot-jarjestyksessa [1 2 3 4 8 9 10 12 14])
(def evknumerot-jarjestyksessa [380040 380041 380042 380043 380044 380045 380046 380047 380048 380049])

(def elynumero->lyhenne {1 "UUD"
                         2 "VAR"
                         3 "KAS"
                         4 "PIR"
                         8 "POS"
                         9 "KES"
                         10 "EPO"
                         12 "POP"
                         14 "LAP"})

(def evknumero->lyhenne {380040 "UUD"
                         380041 "LOU"
                         380042 "KAS"
                         380043 "SIS"
                         380044 "KES"
                         380045 "ITA"
                         380046 "EPO"
                         380047 "POH"
                         380048 "PSU"
                         380049 "LAP"})

(def elynumero->nimi {1 "Uusimaa"
                      2 "Varsinais-Suomi"
                      3 "Kaakkois-Suomi"
                      4 "Pirkanmaa"
                      8 "Pohjois-Savo"
                      9 "Keski-Suomi"
                      10 "Etelä-Pohjanmaa"
                      12 "Pohjois-Pohjanmaa"
                      14 "Lappi"})


(def evknumero->nimi {380040 "Uusimaa"
                      380041 "Lounais-Suomi"
                      380042 "Kaakkois-Suomi"
                      380043 "Sisä-Suomi"
                      380044 "Keski-Suomi"
                      380045 "Itä-Suomi"
                      380046 "Etelä-Pohjanmaa"
                      380047 "Pohjanmaa"
                      380048 "Pohjois-Suomen elinvoimakeskus"
                      380049 "Lappi"})

