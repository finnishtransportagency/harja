(ns harja.domain.palvelut.budjettisuunnittelu
  (:require [clojure.spec.alpha :as s]))

(def tallennettava-asia #{:hoidonjohtopalkkio
                          :toimistokulut
                          :erillishankinnat
                          :rahavaraus-lupaukseen-1
                          :kolmansien-osapuolten-aiheuttamat-vahingot
                          :akilliset-hoitotyot
                          :toimenpiteen-maaramitattavat-tyot})

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
(s/def ::arvo ::positive-number?)

(s/def ::jhk (s/keys :req-un [::hoitokausi ::tunnit ::tuntipalkka ::kk-v]))
(s/def ::jhkt (s/coll-of ::jhk))

(s/def ::ajat (s/coll-of ::aika))

(s/def ::tavoitteet (s/coll-of (s/keys :req-un [::hoitokausi ::tavoitehinta ::kattohinta])
                               :kind vector?))

(s/def ::indeksi (s/keys :req-un [::hoitokausi ::vuosi ::arvo]))

(s/def ::tallenna-johto-ja-hallintokorvaukset-kysely (s/keys :req-un [::urakka-id ::toimenkuva ::maksukausi ::jhkt]))
(s/def ::tallenna-johto-ja-hallintokorvaukset-vastaus any?)

(s/def ::tallenna-kustannusarvioitu-tyo-kysely (s/keys :req-un [::urakka-id ::tallennettava-asia ::toimenpide-avain ::summa ::ajat]))
(s/def ::tallenna-kustannusarvioitu-tyo-vastaus any?)

(s/def ::tallenna-kiinteahintaiset-tyot-kysely (s/keys :req-un [::urakka-id ::toimenpide-avain ::ajat ::summa]))
(s/def ::tallenna-kiinteahintaiset-tyot-vastaus any?)

(s/def ::tallenna-budjettitavoite-kysely (s/keys :req-un [::urakka-id ::tavoitteet]))
(s/def ::tallenna-budjettitavoite-vastaus any?)

(s/def ::budjettisuunnittelun-indeksit-kysely (s/keys :req-un [::urakka-id]))
(s/def ::budjettisuunnittelun-indeksit-vastaus (s/coll-of ::indeksi :count 5))