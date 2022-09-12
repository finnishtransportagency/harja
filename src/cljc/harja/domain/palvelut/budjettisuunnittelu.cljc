(ns harja.domain.palvelut.budjettisuunnittelu
  (:require [clojure.spec.alpha :as s]))

(def tallennettava-asia #{:hoidonjohtopalkkio
                          :toimistokulut
                          :erillishankinnat

                          ;; -- Hankintakustannukset --
                          ;; Toimenpiteen rahavaraukset
                          :kolmansien-osapuolten-aiheuttamat-vahingot
                          :akilliset-hoitotyot
                          :tunneleiden-hoidot
                          :rahavaraus-lupaukseen-1
                          :muut-rahavaraukset

                          ;; Toimenpiteen määrämitattavat työt
                          :toimenpiteen-maaramitattavat-tyot
                          ;; --

                          ;; Tilaajan varaukset
                          :tilaajan-varaukset})

(def toimenpide-avaimet #{:paallystepaikkaukset :mhu-yllapito :talvihoito :liikenneympariston-hoito :sorateiden-hoito :mhu-korvausinvestointi :mhu-johto})

(def vuosikohtaisten-toimenkuvien-vertailuvuosi 2022)

(defn vuosikohtaiset-toimenkuvat?
  "Vuodesta 2022 eteenpäin urakan toimenkuvat suunnitellaan vuosikohtaisesti.
  Tätä tarkastusta käytetään monessa paikassa, joten tämä funktio pitää tarkastukset yhtenäisinä"
  [urakan-alkuvuosi]
  (>= urakan-alkuvuosi vuosikohtaisten-toimenkuvien-vertailuvuosi))

(s/def ::positive-int? (s/and integer? #(>= % 0)))
(s/def ::positive-number? (s/and number? #(>= % 0) #(not= % ##Inf)))

(s/def ::osio keyword?)
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

(s/def ::tavoitteet (s/coll-of (s/keys :req-un [::hoitokausi ::tavoitehinta]
                                 :opt-un [::kattohinta])
                               :kind vector?))

(s/def ::indeksi (s/keys :req-un [::vuosi ::indeksikerroin]))

(s/def ::jhk-tieto (s/keys :req-un [::osa-kuukaudesta]
                           :opt-un [::tunnit ::tuntipalkka]))

(s/def ::jhk-tiedot (s/and #(s/valid? ::kuukausi-ajat (mapv (fn [tiedot]
                                                              (select-keys tiedot #{:vuosi :kuukausi}))
                                                            %))
                           (s/coll-of ::jhk-tieto)))

(s/def ::ennen-urakkaa? boolean?)

;; TODO: Lisää ::muutos
(s/def ::tallenna-johto-ja-hallintokorvaukset-kysely (s/keys :req-un [::urakka-id ::ennen-urakkaa? ::jhk-tiedot]
                                                             :opt-un [::toimenkuva-id ::toimenkuva ::maksukausi]))
(s/def ::tallenna-johto-ja-hallintokorvaukset-vastaus any?)

;; TODO: Lisää ::muutos
(s/def ::tallenna-kustannusarvioitu-tyo-kysely (s/keys :req-un [::osio ::urakka-id ::tallennettava-asia ::toimenpide-avain ::ajat]
                                                       :opt-un [::summa]))
(s/def ::tallenna-kustannusarvioitu-tyo-vastaus any?)

;; TODO: Lisää ::muutos
(s/def ::tallenna-kiinteahintaiset-tyot-kysely (s/keys :req-un [::urakka-id ::toimenpide-avain ::ajat]
                                                       :opt-un [::summa]))
(s/def ::tallenna-kiinteahintaiset-tyot-vastaus any?)

(s/def ::tallenna-budjettitavoite-kysely (s/keys :req-un [::urakka-id ::tavoitteet]))
(s/def ::tallenna-budjettitavoite-vastaus any?)

(s/def ::budjettisuunnittelun-indeksit-kysely (s/keys :req-un [::urakka-id]))
(s/def ::budjettisuunnittelun-indeksit-vastaus (s/coll-of
                                                 (s/or :nil nil? :indeksi ::indeksi)
                                                 :count 5))

(s/def ::tallenna-toimenkuva-kysely (s/keys :req-un [::urakka-id ::toimenkuva-id ::toimenkuva]))
(s/def ::tallenna-toimenkuva-vastaus #(or (contains? % :onnistui?)
                                          (contains? % :virhe)))