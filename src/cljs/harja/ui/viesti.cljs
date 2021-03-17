(ns harja.ui.viesti
  "Flash-viestin näyttäminen UI:n päällä, jolla voidaan kertoa käyttäjälle statustietoa operaatioista."
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.dom :as dom]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def viestin-nayttoaika-lyhyt 2500)
(def viestin-nayttoaika-keskipitka 5000)
(def viestin-nayttoaika-pitka 15000)
(def viestin-oletusnayttoaika viestin-nayttoaika-lyhyt)
(def viestin-nayttaika-aareton 0)

;; Viesti on reagent komponentti, joka näytetään.
;; Luokka on jokin bootstrapin alert-* luokista (ilman etuliitettä)
(defonce viesti-sisalto (atom {:viesti nil
                               :luokka nil
                               :nakyvissa? false
                               :kesto nil}))

(defonce toast-viesti-sisalto (atom {:viesti nil
                                     :luokka nil
                                     :nakyvissa? false
                                     :kesto nil}))


(def +bootstrap-alert-classes+ {:success "alert-success"
                                :info "alert-info"
                                :warning "alert-warning"
                                :danger "alert-danger"})

(defn +toast-viesti-luokat+ [luokka]
  (case luokka
    :neutraali "toast-viesti-neutraali"
    :neutraali-ikoni "toast-viesti-neutraali"
    :varoitus "toast-viesti-varoitus"
    "toast-viesti-onnistunut"))


(defn viesti-container
  "Tämä komponentti sisältää flash viestin ja laitetaan päätason sivuun"
  []
  (let [{:keys [viesti luokka nakyvissa? kesto]} @viesti-sisalto]
    (if nakyvissa?
      (do (go (<! (timeout kesto))
              (swap! viesti-sisalto assoc :nakyvissa? false))
          ^{:key "viesti"}
          [:div.modal {:style {:display "block"}}
           ;; PENDING: viestilläkin backdrop, ehkä joku drop shadow olisi riittävä?
           [:div.modal-backdrop.in {:style {:height @dom/korkeus}
                                    :on-click #(swap! viesti-sisalto assoc :nakyvissa? false)}]
           [:div.flash-viesti {:on-click #(swap! viesti-sisalto assoc :nakyvissa? false)}
            [:div.alert {:class (when luokka
                                  (+bootstrap-alert-classes+ luokka))}
             viesti]]])
      ^{:key "ei-viestia"}
      [:div.ei-viestia-nyt])))

(defn toast-viesti-container
  "Tämä komponentti sisältää flash viestin ja laitetaan päätason sivuun"
  []
  (let [{:keys [viesti luokka nakyvissa? kesto]} @toast-viesti-sisalto
        ikoni (case luokka
                :onnistunut (harja.ui.ikonit/livicon-check)
                :varoitus (harja.ui.ikonit/livicon-warning-sign)
                :neutraali-ikoni (harja.ui.ikonit/livicon-info-sign)
                nil)]
    (if nakyvissa?
      (do (when (not= kesto viestin-nayttaika-aareton) (go (<! (timeout kesto))
                                                           (swap! toast-viesti-sisalto assoc :nakyvissa? false)))
          ^{:key "viesti"}
          [:div.toast-viesti-container
           [:div {:on-click #(swap! toast-viesti-sisalto assoc :nakyvissa? false)
                  :class (when luokka (+toast-viesti-luokat+ luokka))}
            (if ikoni
              [harja.ui.ikonit/ikoni-ja-teksti ikoni viesti]
              viesti)]])
      ^{:key "ei-viestia"}
      [:div.ei-viestia-nyt])))

(defn nayta!
  ([viesti] (nayta! viesti :success))
  ([viesti luokka] (nayta! viesti luokka viestin-oletusnayttoaika))
  ([viesti luokka kesto]
   (when-not (:nakyvissa? @viesti-sisalto)
     (reset! viesti-sisalto {:viesti viesti
                             :luokka luokka
                             :nakyvissa? true
                             :kesto kesto}))))

(defn nayta-toast!
  ([viesti] (nayta-toast! viesti :onnistunut))
  ([viesti luokka] (nayta-toast! viesti luokka viestin-oletusnayttoaika))
  ([viesti luokka kesto]
   (when-not (:nakyvissa? @toast-viesti-sisalto)
     (reset! toast-viesti-sisalto {:viesti viesti
                                   :luokka luokka
                                   :nakyvissa? true
                                   :kesto kesto}))))
