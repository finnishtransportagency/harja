(ns harja.ui.taulukko.osa)

(defprotocol Osa
  (piirra-osa [this])
  (osan-id? [this id]))

(defrecord Teksti [osan-id teksti parameters]
  Osa
  (piirra-osa [this]
    (let [{:keys [id class]} (:parameters this)]
      [:div {:class class
             :id id}
       (:teksti this)]))
  (osan-id? [this id]
    (= (:osan-id this) id)))

(defrecord Laajenna [osan-id teksti ])