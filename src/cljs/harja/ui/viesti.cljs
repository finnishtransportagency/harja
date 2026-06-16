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
(def poistumis-animaatio-ms 250)


(defn vakavuustaso-class [vakavuustaso]
  (case vakavuustaso
    :danger "alert-danger alert-important"
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


(defn- aloita-poisto! [viesti-id]
  (when-let [timer (get @viesti-ajastimet viesti-id)]
    (js/clearTimeout timer)
    (swap! viesti-ajastimet dissoc viesti-id))

  (swap! kaikki-viestit
    (fn [viestit]
      (mapv #(if (= (:id %) viesti-id)
               (assoc % :sulkeutuu? true)
               %)
        viestit)))

  (js/setTimeout
    #(poista-viesti! viesti-id)
    poistumis-animaatio-ms))


(defn- lisaa-viesti!
  [viesti vakavuustaso nayttoaika-ms]
  (let [viesti-id (random-uuid)]
    (swap! kaikki-viestit conj {:id viesti-id
                                :viesti viesti
                                :vakavuustaso vakavuustaso})
    (when (and (number? nayttoaika-ms) (pos? nayttoaika-ms))
      (swap! viesti-ajastimet assoc viesti-id
        (js/setTimeout #(aloita-poisto! viesti-id) nayttoaika-ms)))
    viesti-id))


(defn- yksittainen-viesti [alert-data]
  (r/with-let [nakyvissa? (r/atom false)
               timer (js/setTimeout #(reset! nakyvissa? true) 10)]
    [:div
     {:class (str "alert "
               (vakavuustaso-class (:vakavuustaso alert-data))
               " alert-dismissible "
               (cond
                 (:sulkeutuu? alert-data) "closing"
                 @nakyvissa? "show"
                 :else ""))
      :role "alert"
      :style {:width "fit-content"
              :max-width "min(560px, calc(100vw - 2rem))"
              :margin-bottom "12px"
              :pointer-events "auto"}}

     [:div {:class "d-flex"}
      [:div {:class "alert-icon me-3"}
       [alert-icon (:vakavuustaso alert-data)]]

      [:div (:viesti alert-data)]

      [:button
       {:type "button"
        :class "btn-close"
        :aria-label "close"
        :on-click #(aloita-poisto! (:id alert-data))}]]]
    (finally
      (js/clearTimeout timer))))


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
