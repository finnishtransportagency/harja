(ns harja.domain.tehtavaryhma
  (:require [clojure.spec.alpha :as s]
            [specql.rel]
            [specql.transform]
            #?(:clj [harja.kyselyt.specql-db :refer [define-tables]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["tehtavaryhma" ::tehtavaryhma
   {::emo-fkey (specql.rel/has-one ::emo
                                   ::tehtavaryhma
                                   ::id)
    ::tyyppi (specql.transform/transform (specql.transform/to-keyword))}])
