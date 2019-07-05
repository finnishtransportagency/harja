(ns harja.ui.taulukko.jana
  (:require [clojure.spec.alpha :as s]
            [harja.ui.taulukko.protokollat :as p]))

(defrecord Otsikko [otsikot id]
  p/Jana
  (piirra-jana [this]
    [:div {:class "janan-otsikko"}
     (for [o (:otsikot this)
           :let [{:keys [key class nimi id]} o]]
       ^{:key key}
       (p/piirra-osa o))])

  (janan-id? [this id]
    (= (:id this) id))

  (janan-osat [this]
    (:otsikot this)))

(defrecord Rivi [janan-id solut class]
  p/Jana
  (piirra-jana [this]
    [:div {:class (str "janan-rivi "
                       class)}
     (for [solu (:solut this)
           :let [{:keys [osan-id]} solu]]
       (with-meta
         [p/piirra-osa (vary-meta solu
                                  assoc :harja.ui.taulukko.osa/janan-id janan-id)]
         {:key osan-id}))])

  (janan-id? [this id]
    (= (:janan-id this) id))

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

(s/fdef ->Otsikko
        :args (s/cat :otsikot ::otsikot :id ::id)
        :ret ::otsikko-jana)