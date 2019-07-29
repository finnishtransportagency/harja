(ns harja.ui.taulukko.jana
  (:require [clojure.spec.alpha :as s]
            [harja.ui.taulukko.protokollat :as p]))

(defrecord Otsikko [otsikot id]
  p/Jana
  (piirra-jana [this]
    [:div.jana.janan-otsikko.row
     (for [o (:otsikot this)
           :let [{:keys [key class nimi id]} o]]
       ^{:key key}
       (p/piirra-osa o))])

  (janan-id? [this id]
    (= (:id this) id))
  (janan-id [this]
    id)

  (janan-osat [this]
    (:otsikot this)))

(defrecord Rivi [janan-id solut luokat]
  p/Jana
  (piirra-jana [this]
    [:div.jana.janan-rivi.row (when luokat
                           {:class (apply str (interpose " " luokat))})
     (for [solu (:solut this)
           :let [{:keys [osan-id]} solu]]
       (with-meta
         [p/piirra-osa (vary-meta solu
                                  assoc :harja.ui.taulukko.osa/janan-id janan-id)]
         {:key osan-id}))])

  (janan-id? [this id]
    (= (:janan-id this) id))
  (janan-id [this]
    janan-id)

  (janan-osat [this]
    (:solut this)))

;;;;; SPECS ;;;;;

(s/def ::key any?)
(s/def ::id any?)
(s/def ::class string?)
(s/def ::nimi string?)

(s/def ::otsikko-osa (s/keys :req-un [::key ::class ::nimi ::id]))
(s/def ::otsikot (s/coll-of ::otsikko-osa))
(s/def ::otsikko-jana (s/keys :req-un [::otsikot ::id]))

(s/def ::janan-id any?)
(s/def ::solut (s/and sequential?
                      (fn [solut]
                        (every? #(satisfies? p/Osa %) solut))))
(s/def ::luokat (s/nilable (s/coll-of string? :kind set?)))
(s/def ::rivi-jana (s/keys :req-un [::janan-id ::solut ::luokat]))

(s/fdef ->Otsikko
        :args (s/cat :otsikot ::otsikot :id ::id)
        :ret ::otsikko-jana)

(s/fdef ->Rivi
        :args (s/cat :id ::janan-id :solut ::solut :luokat ::luokat)
        :ret ::rivi-jana)