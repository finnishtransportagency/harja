(ns harja.kyselyt.toteumat
  (:require [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]))

(defqueries "harja/kyselyt/toteumat.sql"
  {:positional? true})

(defn onko-olemassa-ulkoisella-idlla? [db ulkoinen-id luoja]
  (log/debug "Tarkistetaan onko olemassa toteuma ulkoisella id:llä " ulkoinen-id " ja luojalla " luoja)
  (:exists (first (onko-olemassa-ulkoisella-idlla db ulkoinen-id luoja))))

(defn onko-olemassa-varustetoteuma? [db toteuma-id tietolaji toimenpide
                                     tr-numero aosa aet losa let puoli]
  (log/debug "Tarkistetaan onko olemassa varustetoteuma.")
  (:exists (first (onko-olemassa-varustetoteuma db
                                                {:toteumaid toteuma-id
                                                :tietolaji tietolaji
                                                :toimenpide toimenpide
                                                :tr_numero tr-numero
                                                :tr_aosa aosa
                                                :tr_aet aet
                                                :tr_losa losa
                                                :tr_let let
                                                :tr_puoli puoli))))
