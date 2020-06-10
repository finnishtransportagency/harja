(ns harja.ui.taulukko.jana
  (:require [clojure.spec.alpha :as s]
            [harja.ui.taulukko.protokollat :as p]
            [harja.loki :as loki]))

(defonce muuta-avain-rivi
         {:id [:janan-id]
          :lapset [:solut]
          :class [:luokat]
          :on-click [:toiminnot :on-click]
          :piillotettu? [:piilotettu?]})
(defonce muuta-avain-rivi-lapsilla
         {:id [:janan-id]
          :lapset [:janat]})

(defrecord Rivi [janan-id solut luokat]
  p/Jana
  (piirra-jana [this]
    (assert (vector? (:solut this)) (str "RIVIN: " janan-id " SOLUT EI OLE VEKTORI"))
    (when-not (:piilotettu? this)
      [:div.jana.janan-rivi.row (into {:class (when luokat
                                        (apply str (interpose " " luokat)))
                               :data-cy janan-id} (:toiminnot this))
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
                              [[:solut %1]])
                           (:solut this)))))
  p/Asia
  (arvo [this avain]
    (get-in this (muuta-avain-rivi avain)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain-rivi))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain-rivi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain-rivi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain-rivi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain-rivi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain-rivi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain-rivi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain-rivi))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain-rivi))

  (paivita-arvo [this avain f]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f)
      this))
  (paivita-arvo [this avain f a1]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f a1)
      this))
  (paivita-arvo [this avain f a1 a2]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f a1 a2)
      this))
  (paivita-arvo [this avain f a1 a2 a3]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f a1 a2 a3)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f a1 a2 a3 a4)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f a1 a2 a3 a4 a5)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (if-let [polku (muuta-avain-rivi avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)
      this)))

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
      (satisfies? p/Jana jana) (first (keep-indexed (fn [index-jana taman-jana]
                                                      (let [taman-janan-polku (p/osan-polku taman-jana jana)
                                                            janan-polku [:janat index-jana]]
                                                        (cond
                                                          (p/janan-id? taman-jana
                                                                       (p/janan-id jana)) [janan-polku]
                                                          taman-janan-polku (into [] (cons janan-polku taman-janan-polku))
                                                          :else nil)))
                                                    (:janat this)))
      (satisfies? p/Osa jana) (first (keep-indexed (fn [index-jana taman-jana]
                                                     (when-let [taman-janan-polku (p/osan-polku taman-jana jana)]
                                                       (into [] (cons [:janat index-jana] taman-janan-polku))))
                                                   (:janat this)))))
  p/Asia
  (arvo [this avain]
    (get-in this (muuta-avain-rivi-lapsilla avain)))

  (aseta-arvo [this k1 a1]
    (p/aseta-asian-arvo this [k1 a1] muuta-avain-rivi-lapsilla))
  (aseta-arvo [this k1 a1 k2 a2]
    (p/aseta-asian-arvo this [k1 a1 k2 a2] muuta-avain-rivi-lapsilla))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3] muuta-avain-rivi-lapsilla))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4] muuta-avain-rivi-lapsilla))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5] muuta-avain-rivi-lapsilla))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6] muuta-avain-rivi-lapsilla))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7] muuta-avain-rivi-lapsilla))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8] muuta-avain-rivi-lapsilla))
  (aseta-arvo [this k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9]
    (p/aseta-asian-arvo this [k1 a1 k2 a2 k3 a3 k4 a4 k5 a5 k6 a6 k7 a7 k8 a8 k9 a9] muuta-avain-rivi-lapsilla))

  (paivita-arvo [this avain f]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f)
      this))
  (paivita-arvo [this avain f a1]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f a1)
      this))
  (paivita-arvo [this avain f a1 a2]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f a1 a2)
      this))
  (paivita-arvo [this avain f a1 a2 a3]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f a1 a2 a3)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f a1 a2 a3 a4)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f a1 a2 a3 a4 a5)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9)
      this))
  (paivita-arvo [this avain f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
    (if-let [polku (muuta-avain-rivi-lapsilla avain)]
      (update-in this polku f a1 a2 a3 a4 a5 a6 a7 a8 a9 a10)
      this)))

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