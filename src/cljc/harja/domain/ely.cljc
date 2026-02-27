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
(def evknumerot-jarjestyksessa [1 2 3 4 5 6 7 8 9 10])

(def elynumero->lyhenne {1 "UUD"
                         2 "VAR"
                         3 "KAS"
                         4 "PIR"
                         8 "POS"
                         9 "KES"
                         10 "EPO"
                         12 "POP"
                         14 "LAP"})

(def evknumero->lyhenne {1 "UUD"
                         2 "LOU"
                         3 "KAS"
                         4 "SIS"
                         5 "KES"
                         6 "ITA"
                         7 "EPO"
                         8 "POH"
                         9 "PSU"
                         10 "LAP"})

(def elynumero->nimi {1 "Uusimaa"
                      2 "Varsinais-Suomi"
                      3 "Kaakkois-Suomi"
                      4 "Pirkanmaa"
                      8 "Pohjois-Savo"
                      9 "Keski-Suomi"
                      10 "Etelä-Pohjanmaa"
                      12 "Pohjois-Pohjanmaa"
                      14 "Lappi"})


(def evknumero->nimi {1 "Uudenmaan elinvoimakeskus"
                      2 "Lounais-Suomen elinvoimakeskus"
                      3 "Kaakkois-Suomen elinvoimakeskus"
                      4 "Sisä-Suomen elinvoimakeskus"
                      5 "Keski-Suomen elinvoimakeskus"
                      6 "Itä-Suomen elinvoimakeskus"
                      7 "Etelä-Pohjanmaan elinvoimakeskus"
                      8 "Pohjanmaan elinvoimakeskus"
                      9 "Pohjois-Suomen elinvoimakeskus"
                      10 "Lapin elinvoimakeskus"})

