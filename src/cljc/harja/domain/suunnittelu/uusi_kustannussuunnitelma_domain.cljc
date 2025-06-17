(ns harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain
  (:require [clojure.spec.alpha :as s]))

(s/def ::hoitovuoden_alkuvuosi #(and (int? %) (pos? %) (> % 2000)))
(s/def ::urakka-id #(and (int? %) (pos? %) (>= % 1) (< % 99999)))
(s/def ::summa number?)
(s/def ::summa_indeksikorjattu #(or (nil? %) (pos? %)))
(s/def ::toimenpideinstanssi number?)
(s/def ::sopimus #(and (pos? %) (number? %)))
(s/def ::id #(or (nil? %) (and (pos? %) (number? %))))
(s/def ::tehtavaryhma #(and (pos? %) (number? %)))
(s/def ::vuosi #(and (int? %) (pos? %) (> % 2000)))
(s/def ::kuukausi #(and (int? %) (pos? %) (>= % 1) (<= % 12)))
(s/def ::kalenterikuukausi string?)

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

(s/def ::erillishankinnat (s/coll-of
                            (s/keys :req-un [::summa
                                             ::summa_indeksikorjattu
                                             ::toimenpideinstanssi
                                             ::vuosi
                                             ::kuukausi
                                             ::sopimus
                                             ::tehtavaryhma
                                             ::kalenterikuukausi]
                              :opt-un [::id])))

(s/def ::kilpailutettavat-hankinnat (s/keys :req-un [::toimenpiteet ::urakka-id]))
(s/def ::erillishankinta (s/keys :req-un [::erillishankinnat ::urakka-id]))
