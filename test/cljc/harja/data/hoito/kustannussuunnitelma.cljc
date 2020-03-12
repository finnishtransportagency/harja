(ns harja.data.hoito.kustannussuunnitelma
  "Täällä voi määrritellä dataa, jota käytetään kustannussuunnitelmissa."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.set :as clj-set]
            [harja.pvm :as pvm]
            [harja.domain.palvelut.budjettisuunnittelu :as bs-p]))

(def ^:dynamic *hoitokaudet* #{1 2 3 4 5})

(s/def ::toimenkuvat-arg (s/or :setti (s/coll-of ::bs-p/toimenkuva :kind set?)
                               :avain #(= % :kaikki)))
(s/def ::maksukaudet-arg (s/or :setti (s/coll-of ::bs-p/maksukausi :kind set?)
                               :avain #(= % :kaikki)))
(s/def ::hoitokaudet-arg (s/or :setti (s/coll-of ::bs-p/hoitokausi :kind set?)
                               :avain #(= % :kaikki)))



(s/def ::aika-kuukaudella-juuri-alkaneelle-urakalle
  (s/with-gen ::bs-p/aika
              (fn []
                (gen/fmap (fn [_]
                            (let [aloitus-vuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))
                                  vuodet (into [] (range aloitus-vuosi (+ aloitus-vuosi 5)))
                                  hoitokauden-vuodet (rand-nth (map (fn [hoitokausi]
                                                                      [(get vuodet (dec hoitokausi))
                                                                       (get vuodet hoitokausi)])
                                                                    *hoitokaudet*))
                                  v-kks (concat
                                          (map #(identity
                                                  {:vuosi (first hoitokauden-vuodet)
                                                   :kuukausi %})
                                               (range 10 13))
                                          (map #(identity
                                                  {:vuosi (second hoitokauden-vuodet)
                                                   :kuukausi %})
                                               (range 1 10)))]
                              (rand-nth v-kks)))
                          (gen/int)))))

(s/def ::aika-vuodella-juuri-alkaneelle-urakalle
  (s/with-gen ::bs-p/aika
              (fn []
                (gen/fmap (fn [_]
                            (let [aloitus-vuosi (pvm/vuosi (first (pvm/paivamaaran-hoitokausi (pvm/nyt))))
                                  vuodet (into [] (range aloitus-vuosi (+ aloitus-vuosi 5)))
                                  vuosi (rand-nth (map (fn [hoitokausi]
                                                         (get vuodet (dec hoitokausi)))
                                                       *hoitokaudet*))]
                              {:vuosi vuosi}))
                          (gen/int)))))

(defn dg-tallenna-kiinteahintaiset-tyo-data-juuri-alkaneelle-urakalle
  [urakka-id toimenpide-avain hoitokaudet]
  {:urakka-id urakka-id
   :toimenpide-avain toimenpide-avain
   ;; Ajoille tämmöinen hirvitys, että saadaan generoitua random dataa, mutta siten,
   ;; että lopulta kaikkien aika on uniikki
   :ajat (binding [*hoitokaudet* hoitokaudet]
           (mapv first
                 (vals (group-by (juxt :vuosi :kuukausi)
                                 (gen/sample (s/gen ::aika-kuukaudella-juuri-alkaneelle-urakalle))))))
   :summa (gen/generate (s/gen ::bs-p/summa))})

(defn tallenna-kiinteahintaiset-tyot-data
  ([urakka-id] (tallenna-kiinteahintaiset-tyot-data urakka-id {}))
  ([urakka-id
    {:keys [toimenpide-avaimet hoitokaudet]
     :or {toimenpide-avaimet :kaikki
          hoitokaudet :kaikki}}]
   (transduce
     (comp
       (filter (fn [toimenpide-avain]
                 (or (= toimenpide-avaimet :kaikki)
                     (contains? toimenpide-avaimet toimenpide-avain))))
       (map (fn [toimenpide-avain]
              (dg-tallenna-kiinteahintaiset-tyo-data-juuri-alkaneelle-urakalle urakka-id toimenpide-avain (if (= :kaikki hoitokaudet)
                                                                                                            #{1 2 3 4 5}
                                                                                                            hoitokaudet)))))
     conj []
     [:paallystepaikkaukset
      :mhu-yllapito
      :talvihoito
      :liikenneympariston-hoito
      :sorateiden-hoito
      :mhu-korvausinvestointi])))

(defn toimenkuvan-maksukaudet [toimenkuva]
  (case toimenkuva
    "hankintavastaava" #{:molemmat}
    "sopimusvastaava" #{:molemmat}
    "vastuunalainen työnjohtaja" #{:molemmat}
    "päätoiminen apulainen" #{:talvi :kesa}
    "apulainen/työnjohtaja" #{:talvi :kesa}
    "viherhoidosta vastaava henkilö" #{:molemmat}
    "harjoittelija" #{:molemmat}))

(defn validoi-toimenkuvan-maksukaudet! [toimenkuva maksukaudet]
  (when-not (clj-set/subset? maksukaudet (toimenkuvan-maksukaudet toimenkuva))
    (throw #?(:clj (Exception. (str "Toimenkuvalla " toimenkuva " ei ole maksukausia " maksukaudet))
              :cljs (js/Error (str "Toimenkuvalla " toimenkuva " ei ole maksukausia " maksukaudet))))))

(defn dg-tallenna-johto-ja-hallintokorvaus-data
  [urakka-id toimenkuva hoitokaudet kk-kertoimet]
  (loop [[maksukausi & l-mkt] (toimenkuvan-maksukaudet toimenkuva)
         data []]
    (if (nil? maksukausi)
      data
      (recur l-mkt
             (conj data
                   {:urakka-id urakka-id
                    :toimenkuva toimenkuva
                    :maksukausi maksukausi
                    :jhkt (mapv (fn [hoitokausi kk-v]
                                  {:hoitokausi hoitokausi :tunnit (gen/generate (s/gen ::bs-p/tunnit))
                                   :tuntipalkka (gen/generate (s/gen ::bs-p/tuntipalkka)) :kk-v kk-v})
                                hoitokaudet (get kk-kertoimet maksukausi))})))))
(defn tallenna-johto-ja-hallintokorvaus-data
  ([urakka-id] (tallenna-johto-ja-hallintokorvaus-data urakka-id {}))
  ([urakka-id
    {:keys [toimenkuvat maksukaudet hoitokaudet]
     :or {toimenkuvat :kaikki maksukaudet :kaikki hoitokaudet :kaikki}}]
   {:pre [(s/valid? ::toimenkuvat-arg toimenkuvat)
          (s/valid? ::maksukaudet-arg maksukaudet)
          (s/valid? ::hoitokaudet-arg hoitokaudet)]}
   (transduce
     (comp
       (filter (fn [{toimenkuva :toimenkuva}]
                 (or (= toimenkuvat :kaikki)
                     (contains? toimenkuvat toimenkuva))))
       (mapcat (fn [{:keys [urakka-id toimenkuva hoitokaudet kk-kertoimet]}]
                 (dg-tallenna-johto-ja-hallintokorvaus-data urakka-id toimenkuva hoitokaudet kk-kertoimet)))
       (filter (fn [{:keys [toimenkuva maksukausi]}]
                 (validoi-toimenkuvan-maksukaudet! toimenkuva (get maksukaudet toimenkuva))
                 (or (= maksukaudet :kaikki)
                     (contains? (get maksukaudet toimenkuva) maksukausi))))
       (map (fn [data]
              (update data :jhkt (fn [jhkt]
                                   (if (= hoitokaudet :kaikki)
                                     jhkt
                                     (filterv (fn [{hoitokausi :hoitokausi}]
                                                (contains? hoitokaudet hoitokausi))
                                              jhkt)))))))
     conj []
     [{:urakka-id urakka-id
       :toimenkuva "hankintavastaava"
       :hoitokaudet (range 0 6)
       :kk-kertoimet {:molemmat (cons 4.5 (repeat 5 12))}}
      {:urakka-id urakka-id
       :toimenkuva "sopimusvastaava"
       :hoitokaudet (range 1 6)
       :kk-kertoimet {:molemmat (repeat 5 12)}}
      {:urakka-id urakka-id
       :toimenkuva "vastuunalainen työnjohtaja"
       :hoitokaudet (range 1 6)
       :kk-kertoimet {:molemmat (repeat 5 12)}}
      {:urakka-id urakka-id
       :toimenkuva "päätoiminen apulainen"
       :hoitokaudet (range 1 6)
       :kk-kertoimet {:kesa (repeat 5 5)
                      :talvi (repeat 5 7)}}
      {:urakka-id urakka-id
       :toimenkuva "apulainen/työnjohtaja"
       :hoitokaudet (range 1 6)
       :kk-kertoimet {:kesa (repeat 5 5)
                      :talvi (repeat 5 7)}}
      {:urakka-id urakka-id
       :toimenkuva "viherhoidosta vastaava henkilö"
       :hoitokaudet (range 1 6)
       :kk-kertoimet {:molemmat (repeat 5 5)}}
      {:urakka-id urakka-id
       :toimenkuva "harjoittelija"
       :hoitokaudet (range 1 6)
       :kk-kertoimet {:molemmat (repeat 5 4)}}])))

(defn toimenpiteen-tallennettavat-asiat
  [toimenpide-avain]
  (case toimenpide-avain
    :paallystepaikkaukset #{:toimenpiteen-maaramitattavat-tyot}
    :mhu-yllapito #{:rahavaraus-lupaukseen-1
                    :toimenpiteen-maaramitattavat-tyot}
    :talvihoito #{:kolmansien-osapuolten-aiheuttamat-vahingot
                  :akilliset-hoitotyot
                  :toimenpiteen-maaramitattavat-tyot}
    :liikenneympariston-hoito #{:kolmansien-osapuolten-aiheuttamat-vahingot
                                :akilliset-hoitotyot
                                :toimenpiteen-maaramitattavat-tyot}
    :sorateiden-hoito #{:kolmansien-osapuolten-aiheuttamat-vahingot
                        :akilliset-hoitotyot
                        :toimenpiteen-maaramitattavat-tyot}
    :mhu-korvausinvestointi #{:toimenpiteen-maaramitattavat-tyot}
    :mhu-johto #{:hoidonjohtopalkkio
                 :toimistokulut
                 :erillishankinnat
                 :tilaajan-varaukset}))

(defn validoi-toimenpiteen-tallennettavat-asiat! [toimenpide-avain tallennettavat-asiat]
  (when-not (clj-set/subset? tallennettavat-asiat (toimenpiteen-tallennettavat-asiat toimenpide-avain))
    (throw #?(:clj (Exception. (str "Toimenpide avaimella " toimenpide-avain " ei ole kaikkia seuraavista tallennettavista asioita: " tallennettavat-asiat))
              :cljs (js/Error (str "Toimenpide avaimella " toimenpide-avain " ei ole kaikkia seuraavista tallennettavista asioita: " tallennettavat-asiat))))))

(defn dg-tallenna-kustannusarvioitu-tyo-data-juuri-alkaneelle-urakalle
  [urakka-id toimenpide-avain hoitokaudet]
  (loop [[tallennettava-asia & loput-asiat] (toimenpiteen-tallennettavat-asiat toimenpide-avain)
         data []]
    (if (nil? tallennettava-asia)
      data
      (recur loput-asiat
             (conj data
                   {:urakka-id urakka-id
                    :tallennettava-asia tallennettava-asia
                    :toimenpide-avain toimenpide-avain
                    ;; Ajoille tämmöinen hirvitys, että saadaan generoitua random dataa, mutta siten,
                    ;; että lopulta kaikkien aikojen vuosi on uniikki
                    :ajat (binding [*hoitokaudet* hoitokaudet]
                            (mapv first
                                  (vals (group-by :vuosi
                                                  (gen/sample (s/gen ::aika-vuodella-juuri-alkaneelle-urakalle))))))
                    :summa (gen/generate (s/gen ::bs-p/summa))})))))

(defn tallenna-kustannusarvioitu-tyo-data-juuri-alkaneelle-urakalle
  ([urakka-id] (tallenna-kustannusarvioitu-tyo-data-juuri-alkaneelle-urakalle urakka-id {}))
  ([urakka-id
    {:keys [toimenpide-avaimet tallennettavat-asiat hoitokaudet]
     :or {toimenpide-avaimet :kaikki
          tallennettavat-asiat :kaikki
          hoitokaudet :kaikki}}]
   (transduce
     (comp
       (filter (fn [toimenpide-avain]
                 (or (= toimenpide-avaimet :kaikki)
                     (contains? toimenpide-avaimet toimenpide-avain))))
       (mapcat (fn [toimenpide-avain]
                 (dg-tallenna-kustannusarvioitu-tyo-data-juuri-alkaneelle-urakalle urakka-id toimenpide-avain (if (= :kaikki hoitokaudet)
                                                                                                                #{1 2 3 4 5}
                                                                                                                hoitokaudet))))
       (filter (fn [{tallennettava-asia :tallennettava-asia
                     toimenpide-avain :toimenpide-avain}]
                 (validoi-toimenpiteen-tallennettavat-asiat! toimenpide-avain (get tallennettavat-asiat toimenpide-avain))
                 (or (= tallennettavat-asiat :kaikki)
                     (contains? (get tallennettavat-asiat toimenpide-avain) tallennettava-asia)))))
     conj []
     [:paallystepaikkaukset
      :mhu-yllapito
      :talvihoito
      :liikenneympariston-hoito
      :sorateiden-hoito
      :mhu-korvausinvestointi
      :mhu-johto])))
