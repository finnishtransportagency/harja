(ns harja.ui.viesti
  "Flash-viestin näyttäminen UI:n päällä, jolla voidaan kertoa käyttäjälle statustietoa operaatioista."
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.dom :as dom]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [cljs.core.async :refer [<! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def viestin-nayttoaika-lyhyt 2500)
(def viestin-nayttoaika-keskipitka 5000)
(def viestin-nayttoaika-pitka 15000)
(def viestin-oletusnayttoaika viestin-nayttoaika-lyhyt)
(def viestin-nayttoaika-aareton 0)

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
    :neutraali "toast-viesti neutraali"
    :neutraali-ikoni "toast-viesti neutraali"
    :neutraali-ikoni-keskella "toast-viesti neutraali-keskella"
    :varoitus "toast-viesti varoitus"
    "toast-viesti onnistunut"))

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
  "Tämä komponentti sisältää flash viestin ja laitetaan päätason sivuun
   Mahdolliset luokat ovat :onnistunut, :varoitus, :neutraali, :neutraali-ikoni-keskella ja :neutraali-ikoni"
  []
  (let [{:keys [viesti luokka nakyvissa? kesto tehty]} @toast-viesti-sisalto
        ikoni (case luokka
                :onnistunut (ikonit/livicon-check)
                :varoitus (ikonit/livicon-warning-sign)
                :neutraali-ikoni (ikonit/nelio-info)
                :neutraali-ikoni-keskella (ikonit/nelio-info)
                nil)
        keskella-luokka (case luokka
                          :neutraali-ikoni-keskella "toast-keskella"
                          nil)]
    (if nakyvissa?
      (do (when (not= kesto viestin-nayttoaika-aareton)
            (go (<! (timeout kesto))
              (when (:tehty @toast-viesti-sisalto)
                ;; Korjataan viestin resetointi vanhalla ajalla
                ;; Eli jos tehdään uusi viesti, noudatetaan uusimman viestin kestoa aina
                (let [nyt-timestamp (+ (.getTime (pvm/nyt)) 1)
                      tehty-timestamp (.getTime (:tehty @toast-viesti-sisalto))
                      tehty-plus-kesto (+ tehty-timestamp kesto)]
                  ;; Jos kesto on käytetty loppuun nykyaikaan nähden, poista viesti 
                  (when (> nyt-timestamp tehty-plus-kesto)
                    (swap! toast-viesti-sisalto assoc :nakyvissa? false))))))

        ^{:key "viesti"}
        [:div {:class (str "toast-viesti-container " (or keskella-luokka ""))}
         [:div {:on-click #(swap! toast-viesti-sisalto assoc :nakyvissa? false)
                :class (when luokka (+toast-viesti-luokat+ luokka))}
          (when ikoni [:span ikoni])
          [:span {:style {:padding-left "10px"}} (str " " viesti)]
          ;; Sulje nappi :varoitus sekä :ikoni-keskella tyypeille
          (when (or
                  (= :varoitus luokka)
                  (= :neutraali-ikoni-keskella luokka))
            [:span {:style {:padding-left "16px"}}
             [ikonit/sulje]])]])
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
  ([viesti luokka]
   (nayta-toast! viesti
     luokka
     (if (or
           (= :varoitus luokka)
           (= :neutraali-ikoni-keskella luokka))
       viestin-nayttoaika-aareton
       viestin-oletusnayttoaika)))
  ([viesti luokka kesto]
   (when-not (and
               (= luokka :neutraali-ikoni)
               (:nakyvissa? @toast-viesti-sisalto))
     (reset! toast-viesti-sisalto {:viesti viesti
                                   :luokka luokka
                                   :nakyvissa? true
                                   :kesto kesto
                                   :tehty (pvm/nyt)}))))
