(ns harja.ui.viesti
  "Toast viestit"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.saavutettavuus :as saavutettavuus]))

(defonce kaikki-viestit (atom []))
(defonce viesti-ajastimet (atom {}))

(def viestin-nayttoaika-lyhyt 2500)
(def viestin-nayttoaika-keskipitka 5000)
(def viestin-nayttoaika-pitka 15000)
(def viestin-oletusnayttoaika viestin-nayttoaika-lyhyt)
(def viestin-nayttoaika-aareton 0)


(defn vakavuustaso-class [vakavuustaso]
  (case vakavuustaso
    :danger "alalert-danger alert-important"
    :success "alert-success alert-important"
    :warning "alert-warning alert-important"
    :info "alert-info alert-important"))


(defn toast-luokka->vakavuustaso [luokka]
  (case luokka
    :varoitus :warning
    "varoitus" :warning
    :onnistunut :success
    "onnistunut" :success
    :danger :danger
    "danger" :danger
    :warning :warning
    "warning" :warning
    :info :info
    "info" :info
    :neutraali :info
    "neutraali" :info
    :neutraali-ikoni :info
    "neutraali-ikoni" :info
    :neutraali-ikoni-keskella :info
    "neutraali-ikoni-keskella" :info
    :success))


(defn viesti-luokka->vakavuustaso [luokka]
  (case luokka
    :success :success
    :info :info
    :warning :warning
    :danger :danger
    :info))


(defn vakavuustaso-icon-class [vakavuustaso]
  (case vakavuustaso
    :warning "ti-alert-triangle"
    :success "ti-check"
    :danger "ti-alert-octagon"
    :info "ti-alert-octagon"
    "ti-alert-octagon"))


(defn alert-icon [vakavuustaso]
  [:span.alert-icon
   [:i {:class (str "icon ti " (vakavuustaso-icon-class vakavuustaso))}]])


(defn- poista-viesti!
  [viesti-id]
  (when-let [timer (get @viesti-ajastimet viesti-id)]
    (js/clearTimeout timer)
    (swap! viesti-ajastimet dissoc viesti-id))
  (swap! kaikki-viestit
    (fn [alerts]
      (vec (remove #(= (:id %) viesti-id) alerts)))))


(defn- lisaa-viesti!
  [viesti vakavuustaso nayttoaika-ms]
  (let [viesti-id (random-uuid)]
    (swap! kaikki-viestit conj {:id viesti-id
                                :viesti viesti
                                :vakavuustaso vakavuustaso})
    (when (and (number? nayttoaika-ms) (pos? nayttoaika-ms))
      (swap! viesti-ajastimet assoc viesti-id
        (js/setTimeout #(poista-viesti! viesti-id) nayttoaika-ms)))
    viesti-id))


(defn- yksittainen-viesti [alert-data]
  (let [nakyvissa? (r/atom false)]
    (r/create-class
      {:display-name "Alert"

       :component-did-mount
       (fn []
         (js/setTimeout #(reset! nakyvissa? true) 10))

       :reagent-render
       (fn []
         [:div
          {:class (str "alert "
                    (vakavuustaso-class (:vakavuustaso alert-data))
                    " alert-dismissible fade in"
                    (when @nakyvissa? " show"))
           :role "alert"
           :style {:width "fit-content"
                   :max-width "min(560px, calc(100vw - 2rem))"
                   :margin-bottom "12px"
                   :pointer-events "auto"}}
          [:div {:class "d-flex"}
           [:div {:class "alert-icon me-3"}
            [alert-icon (:vakavuustaso alert-data)]]
           [:div (:viesti alert-data)]
           [:a
            {:class "btn-close"
             :aria-label "close"
             :on-click
             (fn [e]
               (.preventDefault e)
               (reset! nakyvissa? false)
               (js/setTimeout #(poista-viesti! (:id alert-data)) 150))}]]])})))


(defn toast-viesti-container []
  [:div {:class "position-fixed bottom-0 end-0 p-3 d-flex flex-column align-items-end"
         :style {:z-index 1050
                 :pointer-events "none"}}
   (for [alert-data @kaikki-viestit]
     ^{:key (:id alert-data)}
     [yksittainen-viesti alert-data])])


(defn nayta!
  ([viesti] (nayta! viesti :success))
  ([viesti luokka] (nayta! viesti luokka viestin-oletusnayttoaika))
  ([viesti luokka kesto]
   (lisaa-viesti! viesti (viesti-luokka->vakavuustaso luokka) kesto)))


(defn nayta-toast!
  ([viesti] (nayta-toast! viesti :onnistunut))
  ([viesti luokka]
   (nayta-toast! viesti
     luokka
     (if (or (= :varoitus luokka)
           (= :neutraali-ikoni-keskella luokka))
       viestin-nayttoaika-aareton
       viestin-oletusnayttoaika)))
  ([viesti luokka kesto]
   (lisaa-viesti! viesti (toast-luokka->vakavuustaso luokka) kesto)
   (saavutettavuus/aseta-aria-live-viesti! viesti)))
