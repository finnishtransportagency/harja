(ns harja.kyselyt.vesivaylat.vatu-turvalaitteet
  (:require [jeesql.core :refer [defqueries]]
            [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.future :refer :all]
            [namespacefy.core :as namespacefy]
            [jeesql.core :refer [defqueries]]
            [specql.core :as specql]
            [specql.op :as op]
            [specql.rel :as rel]

            [harja.kyselyt.konversio :as konv]
            [taoensso.timbre :as log]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [specql.transform :as xf]
            [clojure.string :as str]
            [harja.geo :as geo]))



(defqueries "harja/kyselyt/vesivaylat/vatu_turvalaitteet.sql")

;;TODO: Täydennä kun turvalaiterefactorointi tehdään (vrt. kyselyt.vesivaylat.turvalaitteet)
