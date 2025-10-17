(ns harja.kyselyt.integraatioloki
  "Integraatiotapahtumiin liittyvät tietokantakyselyt"
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/integraatioloki.sql"
  {:positional? true})

(declare hae-integraation-id luo-integraatioviesti<! luo-integraatiotapahtuma<!
  merkitse-integraatiotapahtuma-paattyneeksi!
  merkitse-integraatiotapahtuma-paattyneeksi-ulkoisella-idlla<! aseta-ulkoinen-id-integraatiotapahtumalle!)
