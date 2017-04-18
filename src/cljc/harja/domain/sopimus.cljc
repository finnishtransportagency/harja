(ns harja.domain.sopimus
  "Määrittelee urakkaan liittyvien sopimuksien nimiavaruuden specit"
  (:require [clojure.spec :as s]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            #?@(:clj [[clojure.future :refer :all]])))

(s/def ::id ::spec-apurit/postgres-serial)