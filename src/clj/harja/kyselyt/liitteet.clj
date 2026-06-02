(ns harja.kyselyt.liitteet
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/liitteet.sql"
  {:positional? true})

(declare tallenna-liite<! hae-liite-lataukseen hae-siltatarkastusliite-lataukseen hae-pikkukuva-lataukseen
  hae-urakan-liite-id poista-laatupoikkeaman-kommentin-liite! poista-turvallisuuspoikkeaman-kommentin-liite!
  hae-liite-meta-tiedoilla merkitse-liite-virustarkistetuksi! liite-virustarkastettu? hae-liitteiden-tiedot)
