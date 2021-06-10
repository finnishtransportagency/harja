(ns harja.kyselyt.valikatselmus
  (:require [specql.core :refer [fetch update! insert!]]
            [harja.domain.kulut.valikatselmus :as valikatselmus]))

(defn tee-oikaisu [db oikaisu]
  (insert! db ::valikatselmus/tavoitehinnan-oikaisu oikaisu))