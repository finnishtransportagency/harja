(ns harja.ui.viesti
  "Flash-viestin näyttäminen UI:n päällä, jolla voidaan kertoa käyttäjälle statustietoa operaatioista."
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.yleiset :as yleiset]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Viesti on reagent komponentti, joka näytetään.
;; Luokka on jokin bootstrapin alert-* luokista (ilman etuliitettä)
(defonce viesti-sisalto (atom {:viesti nil
                               :luokka nil
                               :nakyvissa? false
                               :kesto nil}))


(def +bootstrap-alert-classes+ {:success "alert-success"
                                :info "alert-info"
                                :warning "alert-warning"
                                :danger "alert-danger"})


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
           [:div.modal-backdrop.in {:style {:height @yleiset/korkeus}
                                    :on-click #(swap! viesti-sisalto assoc :nakyvissa? false)}]
           [:div.flash-viesti {:on-click #(swap! viesti-sisalto assoc :nakyvissa? false)}
            [:div.alert {:class (when luokka
                                  (+bootstrap-alert-classes+ luokka))}
            viesti]]])
      ^{:key "ei-viestia"}
      [:div.ei-viestia-nyt])))



(defn nayta!
  ([viesti] (nayta! viesti :success))
  ([viesti luokka] (nayta! viesti :success 1500))
  ([viesti luokka kesto]
    (reset! viesti-sisalto {:viesti viesti
                                    :luokka luokka
                                    :nakyvissa? true
                                    :kesto kesto})))

(defn ^:export kokeile []
  (nayta! "Kokeillaan viestiä!"))
