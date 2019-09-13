(ns harja.ui.taulukko.jana
  (:require [clojure.spec.alpha :as s]
            [harja.ui.taulukko.protokollat :as p]))

(defrecord Rivi [janan-id solut luokat]
  p/Jana
  (piirra-jana [this]
    (assert (vector? (:solut this)) (str "RIVIN: " janan-id " SOLUT EI OLE VEKTORI"))
    (when-not (:piilotettu? this)
      [:div.jana.janan-rivi.row {:class (when luokat
                                        (apply str (interpose " " luokat)))
                               :data-cy janan-id}
     (for [solu (:solut this)
           :let [{:keys [osan-id]} solu]]
       (with-meta
         [p/piirra-osa solu]
         {:key osan-id}))]))

  (janan-id? [this id]
    (= (:janan-id this) id))
  (janan-id [this]
    (:janan-id this))
  (janan-osat [this]
    (:solut this))
  (osan-polku [this osa]
    (when (satisfies? p/Osa osa)
      (first (keep-indexed #(when (= (p/osan-id %2) (p/osan-id osa))
                              [:solut %1])
                           (:solut this))))))

(defrecord RiviLapsilla [janan-id janat]
  p/Jana
  (piirra-jana [this]
    (assert (vector? (:janat this)) (str "RIVILAPSILLE: " janan-id " JANAT EI OLE VEKTORI"))
    (let [[vanhempi & lapset] (:janat this)]
      [:<>
       [p/piirra-jana vanhempi]
       (for [lapsi lapset]
         (with-meta [p/piirra-jana lapsi]
                    {:key (p/janan-id lapsi)}))]))

  (janan-id? [this id]
    (= (:janan-id this) id))
  (janan-id [this]
    (:janan-id this))
  (janan-osat [this]
    (:janat this))
  (osan-polku [this jana]
    (cond
      (satisfies? p/Jana jana) (first (keep-indexed #(when (= (p/janan-id %2) (p/janan-id jana))
                                                       [:janat %1])
                                                    (:janat this)))
      (satisfies? p/Osa jana) (first (keep-indexed (fn [index-jana taman-jana]
                                                     (when-let [taman-janan-polku (p/osan-polku taman-jana jana)]
                                                       (into []
                                                             (concat [:janat index-jana] taman-janan-polku))))
                                                   (:janat this))))))

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