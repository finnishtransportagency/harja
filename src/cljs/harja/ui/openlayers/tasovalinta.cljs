(ns harja.ui.openlayers.tasovalinta
  "Openlayers komponentin tasojen valintakontrolli.
  Kaikille taustakarttatasoille, jotka ovat valittavissa, lisätään
  kartalle on/off ikoninappi, josta tason saa kytkettyä päälle tai pois"
  (:require [reagent.core :as r]
            [ol.control.Control]))

(defn- control [content-component]
  (let [elt (js/document.createElement "span")
        comp (r/render [content-component] elt)]
    (ol.control.Control. #js {:element elt})))

(defn- layer-icon [icon visible? nimi the-layer on-change]
  (r/create-class
   {:reagent-render (fn [icon visible? nimi _]
                      [:button.ol-layer-icon.klikattava
                       {:title (str (if visible? "Piilota" "Näytä") " " nimi)
                        :class (if visible?
                                 "ol-layer-icon-on"
                                 "ol-layer-icon-off")}
                       icon])

    ;; Tämä manuaalinen on-click käsittelijän lisäys on valitettavasti pakko tehdä,
    ;; koska ol3/React eventtien käsittelyeroista johtuen :on-click asetetut
    ;; handlerit eivät koskaan laukea kontrollien stopevent parentin takia.
    :component-did-mount (fn [this]
                           (let [d (r/dom-node this)]
                             (set! (.-onclick d)
                                   #(do
                                      (.setVisible the-layer
                                                   (not (.getVisible the-layer)))
                                      (on-change)))))}))

(defn- geometry-layer-icon [_icon _nimi atom on-change]
  (r/create-class
    {:reagent-render (fn [icon nimi atom _]
                       [:button.ol-layer-icon.klikattava
                        {:title (str (if @atom "Piilota" "Näytä") " " nimi)
                         :class (if @atom
                                  "ol-layer-icon-on"
                                  "ol-layer-icon-off")}
                        icon])

     ;; Tämä manuaalinen on-click käsittelijän lisäys on valitettavasti pakko tehdä,
     ;; koska ol3/React eventtien käsittelyeroista johtuen :on-click asetetut
     ;; handlerit eivät koskaan laukea kontrollien stopevent parentin takia.
     :component-did-mount (fn [this]
                            (let [d (r/dom-node this)]
                              (set! (.-onclick d)
                                #(do
                                   (swap! atom not)
                                   (on-change)))))}))

(defn tasovalinta
  "Tasovalinnan komponentti. Tekee openlayers kontrollin, jossa on on/off
  nappi kaikille valittaville tasoille."
  [ol3 layers geometry-layers]
  (let [a (r/atom 0) ; vain indikoimaan reagentille uudelleenpiirron tarve
        re-render! #(swap! a inc)]
    (control
     (fn []
       @a
       [:div.ol-control.ol-layer-icons
        (doall
         (keep-indexed
          (fn [layer-index {:keys [id icon nimi]}]
            (let [the-layer (.item (.getLayers ol3) layer-index)
                  visible? (.getVisible the-layer)]
              (when (and icon id)
                ^{:key layer-index}
                [layer-icon icon visible? nimi the-layer re-render!])))
          layers))

        (doall
          (keep-indexed
            (fn [layer-index {:keys [id icon nimi nakyvissa-atom]}]
              (when (and icon id nakyvissa-atom)
                ^{:key layer-index}
                [geometry-layer-icon icon nimi nakyvissa-atom re-render!]))
            geometry-layers))]))))
