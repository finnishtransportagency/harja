(ns harja.kyselyt.liitteet
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/liitteet.sql"
  {:positional? true})

(declare hae-liitteiden-tiedot hae-urakan-liite-id tallenna-liite<! hae-liite-lataukseen
  hae-siltatarkastusliite-lataukseen hae-pikkukuva-lataukseen
  poista-laatupoikkeaman-kommentin-liite! poista-turvallisuuspoikkeaman-kommentin-liite!
  hae-liite-meta-tiedoilla merkitse-liite-virustarkistetuksi! liite-virustarkastettu?)
