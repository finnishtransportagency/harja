(ns harja.domain.laadunseuranta.laatupoikkeama
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.set :as set]
    [harja.pvm :as pvm]
    #?@(:clj  [
    [harja.kyselyt.specql-db :refer [define-tables]]
    ]
        :cljs [[specql.impl.registry]])
    [harja.domain.urakka :as ur]
    [harja.domain.kayttaja :as kayttaja]
    [harja.domain.muokkaustiedot :as muokkaustiedot])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["laatupoikkeama" ::laatupoikkeama
   ;; TODO Lisää uudelleennimeämisiä sitä mukaan kun tarvii (-id viittauksille jne.)
   {"urakka" ::urakka-id}])

(defn kuvaile-tekija [tekija]
  (case tekija
    :tilaaja "Tilaaja"
    :urakoitsija "Urakoitsija"
    :konsultti "Konsultti"
    ""))

(defn kuvaile-kasittelytapa [kasittelytapa]
  (case kasittelytapa
    :tyomaakokous "Työmaakokous"
    :puhelin "Puhelimitse"
    :kommentit "Harja-kommenttien perusteella"
    :muu "Muu tapa"
    nil))

(defn kuvaile-paatostyyppi [paatos]
  (case paatos
    :sanktio "Sanktio"
    :ei_sanktiota "Ei sanktiota"
    :hylatty "Hylätty"))

(defn kuvaile-paatos [{:keys [kasittelyaika paatos kasittelytapa]}]
  (when paatos
    (str
      (pvm/pvm kasittelyaika)
      " "
      (kuvaile-paatostyyppi paatos)
      " ("
      (kuvaile-kasittelytapa kasittelytapa) ")")))
