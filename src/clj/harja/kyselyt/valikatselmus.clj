(ns harja.kyselyt.valikatselmus
  (:require [specql.core :refer [fetch update! insert! columns]]
            [jeesql.core :refer [defqueries]]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.domain.muokkaustiedot :as muokkaustiedot]
            [harja.domain.urakka :as urakka]))

(defqueries "harja/kyselyt/valikatselmus.sql"
            {:positional? true})

;; TODO: Urakan kustannuksien määrä halutulle kaudelle pitäisi hakea.
(defn hae-kustannukset [db urakka]
  (throw (Exception. "Ei implementoitu vielä")))

(defn hae-oikaisut [db {::urakka/keys [id]}]
  (fetch db ::valikatselmus/tavoitehinnan-oikaisu
         (columns ::valikatselmus/tavoitehinnan-oikaisu)
         {::urakka/id id ::muokkaustiedot/poistettu? false}))

(defn tee-oikaisu [db oikaisu]
  (insert! db ::valikatselmus/tavoitehinnan-oikaisu oikaisu))

(defn paivita-oikaisu [db oikaisu]
  (update! db ::valikatselmus/tavoitehinnan-oikaisu
           oikaisu
           {::valikatselmus/oikaisun-id (::valikatselmus/oikaisun-id oikaisu)}))

(defn poista-oikaisu [db oikaisu]
  (update! db ::valikatselmus/tavoitehinnan-oikaisu
           {::muokkaustiedot/poistettu? true}
           {::valikatselmus/oikaisun-id (::valikatselmus/oikaisun-id oikaisu)}))

(defn tee-paatos [db paatos]
  (insert! db ::valikatselmus/urakka-paatos paatos))