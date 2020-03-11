(ns harja.domain.palvelut.budjettisuunnittelu
  (:require [clojure.spec.alpha :as s]))

(def tallennettava-asia #{:hoidonjohtopalkkio
                          :toimistokulut
                          :erillishankinnat
                          :rahavaraus-lupaukseen-1
                          :kolmansien-osapuolten-aiheuttamat-vahingot
                          :akilliset-hoitotyot
                          :toimenpiteen-maaramitattavat-tyot
                          :tilaajan-varaukset})

(def toimenpide-avaimet #{:paallystepaikkaukset :mhu-yllapito :talvihoito :liikenneympariston-hoito :sorateiden-hoito :mhu-korvausinvestointi :mhu-johto})

(s/def ::positive-int? (s/and integer? #(>= % 0)))
(s/def ::positive-number? (s/and number? #(>= % 0) #(not= % ##Inf)))

(s/def ::vuosi ::positive-int?)
(s/def ::kuukausi (s/and integer?
                         #(<= 1 % 12)))
(s/def ::urakka-id ::positive-int?)
(s/def ::toimenkuva string?)
(s/def ::maksukausi keyword?)
(s/def ::hoitokausi (s/and integer?
                           #(<= 0 % 5)))
(s/def ::tunnit ::positive-number?)
(s/def ::tuntipalkka ::positive-number?)
(s/def ::kk-v (s/and number?
                     #(not= % ##Inf)
                     #(<= 1 % 12)))
(s/def ::toimenkuva-id ::positive-int?)
(s/def ::summa ::positive-number?)
(s/def ::tavoitehinta ::positive-number?)
(s/def ::kattohinta ::positive-number?)
(s/def ::toimenpide-avain (s/and keyword?
                                 (fn [k]
                                   (some #(= k %)
                                         toimenpide-avaimet))))
(s/def ::tallennettava-asia tallennettava-asia)
(s/def ::aika (s/keys :req-un [::vuosi]
                      :opt-un [::kuukausi]))
(s/def ::kuukausi-aika (s/keys :req-un [::vuosi ::kuukausi]))
(s/def ::osa-kuukaudesta #(<= 0 % 1))
(s/def ::indeksikerroin ::positive-number?)

(s/def ::ajat (s/and (s/coll-of ::aika)
                     (fn [ajat]
                       (= (count ajat)
                          (count (distinct ajat))))
                     (fn [ajat]
                       (or (every? (and #(contains? % :vuosi)
                                        #(contains? % :kuukausi))
                                   ajat)
                           (every? (and #(contains? % :vuosi)
                                        #(not (contains? % :kuukausi)))
                                   ajat)))))

(s/def ::kuukausi-ajat (s/and (s/coll-of ::kuukausi-aika)
                              (s/or :urakan-aikana (fn [ajat]
                                                     (= (count ajat)
                                                        (count (distinct ajat))))
                                    :ennen-urakkaa (fn [ajat]
                                                     (= 1 (count (distinct ajat)))))))

(s/def ::tavoitteet (s/coll-of (s/keys :req-un [::hoitokausi ::tavoitehinta ::kattohinta])
                               :kind vector?))

(s/def ::indeksi (s/keys :req-un [::vuosi ::indeksikerroin]))

(s/def ::jhk-tieto (s/keys :req-un [::tunnit ::tuntipalkka ::osa-kuukaudesta]))

(s/def ::jhk-tiedot (s/and #(s/valid? ::kuukausi-ajat (mapv (fn [tiedot]
                                                              (select-keys tiedot #{:vuosi :kuukausi}))
                                                            %))
                           (s/coll-of ::jhk-tieto)))

(s/def ::ennen-urakkaa? boolean?)

(s/def ::tallenna-johto-ja-hallintokorvaukset-kysely (s/keys :req-un [::urakka-id ::ennen-urakkaa? ::jhk-tiedot]
                                                             :opt-un [::toimenkuva-id ::toimenkuva]))
(s/def ::tallenna-johto-ja-hallintokorvaukset-vastaus any?)

(s/def ::tallenna-kustannusarvioitu-tyo-kysely (s/keys :req-un [::urakka-id ::tallennettava-asia ::toimenpide-avain ::summa ::ajat]))
(s/def ::tallenna-kustannusarvioitu-tyo-vastaus any?)

(s/def ::tallenna-kiinteahintaiset-tyot-kysely (s/keys :req-un [::urakka-id ::toimenpide-avain ::ajat ::summa]))
(s/def ::tallenna-kiinteahintaiset-tyot-vastaus any?)

(s/def ::tallenna-budjettitavoite-kysely (s/keys :req-un [::urakka-id ::tavoitteet]))
(s/def ::tallenna-budjettitavoite-vastaus any?)

(s/def ::budjettisuunnittelun-indeksit-kysely (s/keys :req-un [::urakka-id]))
(s/def ::budjettisuunnittelun-indeksit-vastaus (s/coll-of ::indeksi :count 5))

(s/def ::tallenna-toimenkuva-kysely (s/keys :req-un [::urakka-id ::toimenkuva-id ::toimenkuva]))
(s/def ::tallenna-toimenkuva-vastaus #(or (contains? % :onnistui?)
                                          (contains? % :virhe)))