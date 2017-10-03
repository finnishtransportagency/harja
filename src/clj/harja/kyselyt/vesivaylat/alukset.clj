(ns harja.kyselyt.vesivaylat.alukset
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.future :refer :all]
            [jeesql.core :refer [defqueries]]
            [taoensso.timbre :as log]
            [harja.id :refer [id-olemassa?]]
            [harja.pvm :as pvm]
            [clj-time.core :as t]))

(defqueries "harja/kyselyt/vesivaylat/alukset.sql"
  {:positional? true})