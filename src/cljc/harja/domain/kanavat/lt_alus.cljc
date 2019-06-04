(ns harja.domain.kanavat.lt-alus
  (:require
    [clojure.string :as str]
    [clojure.spec.alpha :as s]
    [specql.transform :as xf]
    [clojure.set]
    [specql.rel :as rel]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])

    [harja.domain.muokkaustiedot :as m])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["liikennetapahtuma_aluslaji" ::lt-aluslaji (specql.transform/transform (specql.transform/to-keyword))]
  ["liikennetapahtuma_suunta" ::aluksen-suunta (specql.transform/transform (specql.transform/to-keyword))]
  ["kan_liikennetapahtuma_alus" ::liikennetapahtuman-alus
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistaja-sarake
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::liikennetapahtuma (specql.rel/has-one ::liikennetapahtuma-id
                                            :harja.domain.kanavat.liikennetapahtuma/liikennetapahtuma
                                            :harja.domain.kanavat.liikennetapahtuma/id)}])

(def perustiedot
  #{::id
    ::nimi
    ::laji
    ::lkm
    ::matkustajalkm
    ::nippulkm
    ::suunta})

(s/def ::aluslajit (s/coll-of ::laji))

(def metatiedot m/muokkauskentat)

(def aluslajit*
  {:HIN ["HIN" "Hinaaja"]
   :HUV ["HUV" "Huvivene"]
   :LAU ["LAU" "Lautta"]
   :MAT ["MAT" "Matkustajalaiva"]
   :PRO ["PRO" "Proomu"]
   :RAH ["RAH" "Rahtilaiva"]
   :SEK ["SEK" "Sekalainen"]
   :ÖLJ ["ÖLJ" "Öljylaiva"]})

(def aluslajit (keys aluslajit*))

(defn aluslaji->koko-str [laji]
  (when-let [laji (aluslajit* laji)]
    (str (first laji) " - " (second laji))))

(defn aluslaji->laji-str [laji]
  (first (aluslajit* laji)))

(defn suodata-alukset-nimen-alulla [alukset nimi]
  (filter #(str/starts-with?
             (str/lower-case (::nimi %))
             (str/lower-case nimi))
          alukset))
