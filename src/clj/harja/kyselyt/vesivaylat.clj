(ns harja.kyselyt.vesivaylat
  (:require [jeesql.core :refer [defqueries]]
            [specql.core :refer [fetch]]
            [specql.op :as op]
            [specql.rel :as rel]
            [clojure.spec :as s]
            [harja.kyselyt.specql-db :refer [define-tables]]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.vesivaylat.urakoitsija :as vv-urakoitsija]
            [harja.domain.vesivaylat.alus :as vv-alus]
            [harja.domain.vesivaylat.turvalaite :as vv-turvalaite]
            [harja.domain.vesivaylat.toimenpide :as vv-toimenpide]
            [harja.domain.vesivaylat.sopimus :as vv-sopimus]
            [harja.domain.vesivaylat.vayla :as vv-vayla]
            [clojure.future :refer :all]))



(define-tables
  ["reimari_urakoitsija" ::vv-urakoitsija/urakoitsija]
  ["reimari_sopimus" ::vv-sopimus/sopimus]
  ["reimari_turvalaite" ::vv-turvalaite/turvalaite]
  ["reimari_alus" ::vv-alus/alus]
  ["reimari_vayla" ::vv-vayla/vayla]
  ["reimari_turvalaite" ::vv-turvalaite/turvalaite]
  ["reimari_toimenpide" ::vv-toimenpide/toimenpide
   {"muokattu" ::m/muokattu
    "muokkaaja" ::m/muokkaaja-id
    "luotu" ::m/luotu
    "luoja" ::m/luoja-id
    "poistettu" ::m/poistettu? ;; FIXME: poistettu on TIMESTAMP tietyöilmoituksessa
    "poistaja" ::m/poistaja-id}])

(def kaikki-toimenpiteen-kentat
  #{::vv-toimenpide/id
    ::vv-toimenpide/reimari-id
    ::vv-toimenpide/tyolaji
    ::vv-toimenpide/tyoluokka
    ::vv-toimenpide/tyyppi
    ::vv-toimenpide/lisatieto
    ::vv-toimenpide/lisatyo
    ::vv-toimenpide/tila
    ::vv-toimenpide/suoritettu
    ::vv-toimenpide/reimari-luotu
    ::vv-toimenpide/reimari-muokattu
    ::vv-toimenpide/asiakas
    ::vv-toimenpide/vastuuhenkilo

    ::vv-toimenpide/alus
    ::vv-toimenpide/urakoitsija
    ::vv-toimenpide/sopimus
    ::vv-toimenpide/turvalaite
    ::vv-toimenpide/vayla

    ::m/muokattu
    ::m/muokkaaja-id
    ::m/luotu
    ::m/luoja-id
    ::m/poistettu?
    ::m/poistaja-id
    })


(defn hae-toimenpiteet [db {:keys [luotu-alku
                                   luotu-loppu
                                   urakoitsija-id]}]
  (let [toimenpiteet (fetch db ::vv-toimenpide/toimenpide kaikki-toimenpiteen-kentat
                           (op/and
                            (merge {}
                                    (when (and luotu-alku luotu-loppu)
                                      {::m/reimari-luotu (op/between luotu-alku luotu-loppu)})
                                    (when urakoitsija-id
                                      {::vv-toimenpide/urakoitsija {::vv-urakoitsija/id urakoitsija-id}}))))]
    toimenpiteet))
