(ns harja.ui.tom
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))


(defn tom-select [attrs & children]
  (let [Core
        (r/create-class
          {:display-name "tom-select-core"
           :component-did-mount
           (fn [this]
             (when js/TomSelect
               (let [el (rdom/dom-node this)
                     p (r/props this)
                     inst (js/TomSelect. el (or (:ts/options p) #js {}))
                     reposition (fn []
                                  ;; Fixes dropdowns
                                  (let [^js dp (.-dropdown inst)
                                        ^js ctl (.-control inst)
                                        r (.getBoundingClientRect ctl)]
                                    (set! (.. dp -style -position) "fixed")
                                    (set! (.. dp -style -left) (str (.-left r) "px"))
                                    (set! (.. dp -style -top) (str (.-bottom r) "px"))
                                    (set! (.. dp -style -width) (str (.-width r) "px"))))]
                 (set! (.-_ts this) inst)
                 (.on inst "dropdown_open" (fn [] (reposition)
                                             (.addEventListener js/window "resize" reposition)))
                 (.on inst "dropdown_close" (fn [] (.removeEventListener js/window "resize" reposition)))
                 (when-let [v (:value p)] (.setValue inst (clj->js v) true)))))

           :component-did-update
           (fn [this _]
             (when-let [inst (.-_ts this)]
               (when-let [v (:value (r/props this))]
                 (.setValue inst (clj->js v) true))))

           :component-will-unmount
           (fn [this] (some-> (.-_ts this) .destroy))

           :reagent-render
           (fn [attrs & props]
             (into [:select.form-select (dissoc attrs :ts/options :value)] props))})]

    (into [Core attrs] children)))


(defn- esimerkki []
  [tom-select
   {:id "select-tags"
    :multiple true
    :placeholder "Select tags"
    :data-bs-toggle "select"
    :ts/options #js {:copyClassesToDropdown false
                     :dropdownParent "body"
                     :controlInput "<input>"}}
   [:option {:value "HTML"} "HTML"]
   [:option {:value "JavaScript"} "JavaScript"]
   [:option {:value "CSS"} "CSS"]
   [:option {:value "jQuery"} "jQuery"]
   [:option {:value "Bootstrap"} "Bootstrap"]
   [:option {:value "Ruby"} "Ruby"]
   [:option {:value "Python"} "Python"]])
