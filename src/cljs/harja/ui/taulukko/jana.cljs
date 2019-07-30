(ns harja.ui.taulukko.jana
  (:require [clojure.spec.alpha :as s]
            [harja.ui.taulukko.protokollat :as p]))

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
    (:janan-id this))

  (janan-osat [this]
    (:solut this)))

;;;;; SPECS ;;;;;

(s/def ::key any?)
(s/def ::id any?)
(s/def ::janan-id any?)
(s/def ::class string?)
(s/def ::nimi string?)
(s/def ::solu #(satisfies? p/Osa %))
(s/def ::solut (s/coll-of ::solu))


(s/def ::luokat (s/nilable (s/coll-of string? :kind set?)))
(s/def ::rivi-jana (s/keys :req-un [::janan-id ::solut ::luokat]))

(s/fdef ->Rivi
        :args (s/cat :id ::janan-id :solut ::solut :luokat ::luokat)
        :ret ::rivi-jana)