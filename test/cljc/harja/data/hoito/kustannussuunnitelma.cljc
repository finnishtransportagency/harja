(ns harja.data.hoito.kustannussuunnitelma
  "Täällä voi määrritellä dataa, jota käytetään kustannussuunnitelmissa."
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.set :as clj-set]
            [harja.palvelin.palvelut.budjettisuunnittelu :as bs]))

(s/def ::toimenkuvat-arg (s/or :setti (s/coll-of ::bs/toimenkuva :kind set?)
                               :avain #(= % :kaikki)))
(s/def ::maksukaudet-arg (s/or :setti (s/coll-of ::bs/maksukausi :kind set?)
                               :avain #(= % :kaikki)))
(s/def ::hoitokaudet-arg (s/or :setti (s/coll-of ::bs/hoitokausi :kind set?)
                               :avain #(= % :kaikki)))

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
                                  {:hoitokausi hoitokausi :tunnit (gen/generate (s/gen ::bs/tunnit))
                                   :tuntipalkka (gen/generate (s/gen ::bs/tuntipalkka)) :kk-v kk-v})
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
                 (cond
                   (= toimenkuvat :kaikki) true
                   (contains? toimenkuvat toimenkuva) true
                   :else false)))
       (mapcat (fn [{:keys [urakka-id toimenkuva hoitokaudet kk-kertoimet]}]
                 (dg-tallenna-johto-ja-hallintokorvaus-data urakka-id toimenkuva hoitokaudet kk-kertoimet)))
       (filter (fn [{:keys [toimenkuva maksukausi]}]
                 (validoi-toimenkuvan-maksukaudet! toimenkuva (get maksukaudet toimenkuva))
                 (cond
                   (= maksukaudet :kaikki) true
                   (contains? maksukaudet maksukausi) true
                   :else false)))
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
