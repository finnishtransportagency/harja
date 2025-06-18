(ns harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain
  (:require [clojure.spec.alpha :as s]))


(s/def ::toimenpiteet (s/coll-of
                       (s/keys :req-un [::nimi
                                        ::toimenpideinstanssi-id
                                        ::pysyvat-muutokset
                                        ::alkukausi
                                        ::alkukausi-indeksikorjattu
                                        ::loppukausi
                                        ::loppukausi-indeksikorjattu
                                        ::yhteensa
                                        ::yhteensa-indeksikorjattu]
                         :opt-un [::osio])))

(s/def ::hoitovuoden_alkuvuosi #(and (int? %) (pos? %) (> % 2000)))
(s/def ::urakka-id #(and (int? %) (pos? %) (>= % 1) (< % 99999)))

(s/def ::kustannussuunnitelma (s/keys :req-un [::toimenpiteet ::urakka-id]))
