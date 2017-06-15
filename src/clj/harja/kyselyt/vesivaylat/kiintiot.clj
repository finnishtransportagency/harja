(ns harja.kyselyt.vesivaylat.kiintiot
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [clojure.future :refer :all]
            [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! upsert!]]
            [specql.op :as op]
            [specql.rel :as rel]
            [taoensso.timbre :as log]

            [harja.domain.muokkaustiedot :as m]))

(defn hae-kiintiot [db tiedot]
  (constantly {}))
