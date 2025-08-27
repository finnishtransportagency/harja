(ns harja.domain.suunnittelu.uusi-kustannussuunnitelma-domain
  (:require [clojure.spec.alpha :as s]))

(defn onko-tunnit-samat?
  "Tarkistaa, onko :tunnit arvo sama, jokaisena kuukautena."
  [kuukaudet]
  (let [tunnit-values (map :tunnit kuukaudet)
        distinct-values (distinct (remove nil? tunnit-values))]
    (or (empty? distinct-values)      ;; Arvot eivät olleet samat
      (= 1 (count distinct-values)) ;; Vain yksi distinct arvo tarkoittaa, että kaikki arvot olivat samat
      )))

(s/def ::hoitovuoden_alkuvuosi #(and (int? %) (pos? %) (> % 2000)))
(s/def ::urakka-id #(and (int? %) (pos? %) (>= % 1) (< % 99999)))
(s/def ::alkukausi #(or (nil? %) (and (number? %) (>= % 0))))
(s/def ::alkukausi-indeksikorjattu #(or (nil? %) (and (number? %) (>= % 0))))
(s/def ::loppukausi #(or (nil? %) (and (number? %) (>= % 0))))
(s/def ::loppukausi-indeksikorjattu #(or (nil? %) (and (number? %) (>= % 0))))
(s/def ::summa #(or (nil? %) (and (number? %) (>= % 0))))
(s/def ::summa_indeksikorjattu #(or (nil? %) (and (number? %) (>= % 0))))
(s/def ::summa-indeksikorjattu #(or (nil? %) (and (number? %) (>= % 0))))
(s/def ::toimenpideinstanssi number?)
(s/def ::sopimus #(and (pos? %) (number? %)))
(s/def ::id #(or (nil? %) (and (pos? %) (number? %))))
(s/def ::tehtavaryhma #(and (pos? %) (number? %)))
(s/def ::tehtava #(and (pos? %) (number? %)))
(s/def ::vuosi #(and (int? %) (pos? %) (> % 2000)))
(s/def ::kuukausi #(and (int? %) (pos? %) (>= % 1) (<= % 12)))
(s/def ::kalenterikuukausi string?)
(s/def ::toimenkuva string?)
(s/def ::toimenkuva-id #(and (pos? %) (number? %)))
(s/def ::tunnit #(or (nil? %) (and (number? %) (>= % 0))))
(s/def ::tuntipalkka #(or (nil? %) (and (number? %) (>= % 0))))
(s/def ::tuntipalkka-indeksikorjattu #(or (nil? %) (and (number? %) (>= % 0))))

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
(s/def ::hoidonjohtopalkkiot (s/coll-of
                               (s/keys :req-un [::summa
                                                ::summa_indeksikorjattu
                                                ::toimenpideinstanssi
                                                ::vuosi
                                                ::kuukausi
                                                ::sopimus
                                                ::tehtava
                                                ::kalenterikuukausi]
                                 :opt-un [::id])))

(s/def ::johto-ja-hallintokorvaukset-2025 (s/coll-of
                                            (s/keys :req-un [::summa
                                                             ::summa_indeksikorjattu
                                                             ::vuosi
                                                             ::kuukausi
                                                             ::kalenterikuukausi]
                                              :opt-un [::id])))
(s/def ::jjh-2019 (s/coll-of
                    (s/keys :req-un [::toimenkuva-id
                                     ::toimenkuva
                                     ::tunnit
                                     ::tuntipalkka
                                     ::vuosi
                                     ::kuukausi
                                     ::kalenterikuukausi]
                      :opt-un [::tuntipalkka-indeksikorjattu])))

(s/def ::johto-ja-hallintokorvaukset-2019 (s/coll-of
                                            (s/keys :req-un [::id
                                                             ::toimenkuva
                                                             ::kuukaudet (s/coll-of ::jjh-2019)])))

(s/def ::kilpailutettavat-hankinnat (s/keys :req-un [::toimenpiteet ::urakka-id]))
(s/def ::erillishankinta (s/keys :req-un [::erillishankinnat ::urakka-id]))
(s/def ::hoidonjohtopalkkio (s/keys :req-un [::hoidonjohtopalkkiot ::urakka-id]))
(s/def ::johto-ja-hallintokorvaus-2019 (s/keys :req-un [::johto-ja-hallintokorvaukset-2019 ::urakka-id]))
(s/def ::johto-ja-hallintokorvaus-2025 (s/keys :req-un [::johto-ja-hallintokorvaukset-2025 ::urakka-id]))
