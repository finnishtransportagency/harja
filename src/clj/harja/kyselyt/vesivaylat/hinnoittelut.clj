(ns harja.kyselyt.vesivaylat.hinnoittelut
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch update! insert!]]
            [specql.op :as op]
            [specql.rel :as rel]
            [clojure.spec.alpha :as s]
            [taoensso.timbre :as log]
            [clojure.future :refer :all]
            [clojure.set :as set]

            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]))

(defn hae-hinnoittelut [db tiedot]
  (let
    [urakka-id (::ur/id tiedot)]
    (fetch db
           ::h/hinnoittelu
           h/perustiedot
           {::h/urakka-id urakka-id})))

(defn luo-hinnoittelu! [db user tiedot]
  (let
    [urakka-id (::ur/id tiedot)
     nimi (::h/nimi tiedot)]
    (insert! db
             ::h/hinnoittelu
             {::h/urakka-id urakka-id
              ::h/nimi nimi
              ::h/hintaryhma? true
              ::m/luoja-id (:id user)})))